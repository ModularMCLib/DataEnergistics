package com.fish_dan_.data_energistics.config.persistence;

import com.fish_dan_.data_energistics.Data_Energistics;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.util.function.Supplier;

/** A registered JSON configuration, read at construction and saved through the common file boundary. */
public final class JsonConfiguration<T> {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static final int MAXIMUM_BYTES = 4 * 1024 * 1024;

    private final Path file;
    private final Codec<T> codec;
    private T value;
    private boolean writesDisabled;

    /** Loads and validates immediately. A corrupt file is backed up before defaults can be saved. */
    public JsonConfiguration(Path file, Codec<T> codec, Supplier<T> defaults) {
        this.file = file;
        this.codec = codec;
        this.value = defaults.get();
        try {
            String text = ConfigurationIO.read(file, MAXIMUM_BYTES, "{}");
            this.value = codec.parse(JsonOps.INSTANCE, JsonParser.parseString(text)).getOrThrow();
        } catch (IOException | RuntimeException failure) {
            Data_Energistics.LOGGER.warn("Cannot read configuration at {}; preserving the file before using defaults", file, failure);
            try {
                Path backup = ConfigurationIO.backupCorrupt(file, Clock.systemUTC().millis());
                Data_Energistics.LOGGER.warn("Moved corrupt configuration to {}", backup);
                save(this.value);
            } catch (IOException backupFailure) {
                this.writesDisabled = true;
                Data_Energistics.LOGGER.error("Cannot preserve configuration at {}; further writes are disabled", file, backupFailure);
            }
        }
    }

    /** Client code owns edits to mutable values; immutable candidates become current only after a successful save. */
    public synchronized T get() {
        return this.value;
    }

    /** Saves one complete configuration. Returns false and retains the previous current value on failure. */
    public synchronized boolean save(T candidate) {
        if (this.writesDisabled) return false;
        try {
            String text = encode(candidate);
            if (text.getBytes(StandardCharsets.UTF_8).length > MAXIMUM_BYTES) {
                throw new IOException("Configuration exceeds " + MAXIMUM_BYTES + " bytes");
            }
            ConfigurationIO.write(this.file, text);
            this.value = candidate;
            return true;
        } catch (IOException | RuntimeException failure) {
            Data_Energistics.LOGGER.warn("Cannot save configuration at {}", this.file, failure);
            return false;
        }
    }

    /** Allows bounded history repositories to evict entries before handing a complete snapshot to save. */
    public int encodedBytes(T candidate) {
        return encode(candidate).getBytes(StandardCharsets.UTF_8).length;
    }

    private String encode(T candidate) {
        return GSON.toJson(this.codec.encodeStart(JsonOps.INSTANCE, candidate).getOrThrow()) + "\n";
    }
}
