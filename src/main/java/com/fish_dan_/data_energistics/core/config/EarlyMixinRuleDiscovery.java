package com.fish_dan_.data_energistics.core.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/** Discovers package and exact-class rules from the single Mixin descriptor. */
public final class EarlyMixinRuleDiscovery {

    private static final Logger LOGGER = LogManager.getLogger("DataEnergisticsEarlyConfig");

    private EarlyMixinRuleDiscovery() {}

    public static EarlyConfigBuilder discover(Path configPath, boolean production, boolean datagen) {
        EarlyConfigBuilder builder = new EarlyConfigBuilder(configPath)
                .rule(DataEnergisticsEarlyConfig.NETWORK_BACKED_BLANK_PATTERN_SLOT, true)
                .comment(DataEnergisticsEarlyConfig.NETWORK_BACKED_BLANK_PATTERN_SLOT,
                        "Use the network-backed blank pattern slot; false keeps AE2's local slot.")
                .hidden("dev", !production)
                .hidden("dev.datagen", datagen);
        try (InputStream stream = EarlyMixinRuleDiscovery.class.getResourceAsStream("/data_energistics.mixins.json")) {
            if (stream == null) {
                throw new IOException("Missing data_energistics.mixins.json");
            }
            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                ObjectArrayList<String> sections = new ObjectArrayList<>();
                sections.add("mixins");
                sections.add("client");
                sections.add("server");
                for (String section : sections) {
                    if (!json.has(section)) {
                        continue;
                    }
                    JsonArray mixins = json.getAsJsonArray(section);
                    for (JsonElement mixin : mixins) {
                        if (!mixin.isJsonPrimitive() || !mixin.getAsJsonPrimitive().isString()) {
                            throw new IllegalArgumentException("Non-string Mixin in " + section);
                        }
                        builder.mixin(mixin.getAsString());
                    }
                }
            }
        } catch (IOException | RuntimeException exception) {
            // If metadata cannot be read, do not turn uncertainty into a global Mixin disable.
            // The plugin will find no package rule and will therefore allow injection by default.
            LOGGER.warn("Could not discover Mixin rules; allowing injection by default", exception);
        }
        return builder;
    }
}
