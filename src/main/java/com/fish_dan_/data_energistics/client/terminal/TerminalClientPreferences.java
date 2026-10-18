package com.fish_dan_.data_energistics.client.terminal;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.neoforged.fml.loading.FMLPaths;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jspecify.annotations.NullMarked;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Local geometry only; inventories, ownership and network settings remain server-authoritative. */
@NullMarked
public final class TerminalClientPreferences {

    private static final Path FILE = FMLPaths.CONFIGDIR.get().resolve("data_energistics/player_terminal.json");
    public float xPercent = 0.047058824F;
    public float yPercent = 0.11111111F;
    public int width = 150;
    public int height = 168;
    public boolean folded = true;
    public boolean open = true;

    public static TerminalClientPreferences load() {
        TerminalClientPreferences value = new TerminalClientPreferences();
        if (!Files.exists(FILE)) return value;
        try {
            JsonObject json = JsonParser.parseString(Files.readString(FILE, StandardCharsets.UTF_8)).getAsJsonObject();
            if (json.has("x_percent")) value.xPercent = Math.clamp(json.get("x_percent").getAsFloat(), 0, 1);
            if (json.has("y_percent")) value.yPercent = Math.clamp(json.get("y_percent").getAsFloat(), 0, 1);
            value.width = json.get("width").getAsInt();
            value.height = json.get("height").getAsInt();
            value.folded = json.get("folded").getAsBoolean();
            if (json.has("open")) value.open = json.get("open").getAsBoolean();
        } catch (IOException | RuntimeException failure) {
            Data_Energistics.LOGGER.warn("Cannot load player terminal geometry from {}", FILE, failure);
        }
        return value;
    }

    public void save() {
        JsonObject json = new JsonObject();
        json.addProperty("x_percent", xPercent);
        json.addProperty("y_percent", yPercent);
        json.addProperty("width", width);
        json.addProperty("height", height);
        json.addProperty("folded", folded);
        json.addProperty("open", open);
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, json.toString(), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            Data_Energistics.LOGGER.warn("Cannot save player terminal geometry to {}", FILE, failure);
        }
    }
}
