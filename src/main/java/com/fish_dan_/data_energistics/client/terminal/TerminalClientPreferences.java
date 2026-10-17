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
    public int x = 8;
    public int y = 8;
    public int width = 150;
    public int height = 168;
    public boolean folded = true;

    public static TerminalClientPreferences load() {
        TerminalClientPreferences value = new TerminalClientPreferences();
        if (!Files.exists(FILE)) return value;
        try {
            JsonObject json = JsonParser.parseString(Files.readString(FILE, StandardCharsets.UTF_8)).getAsJsonObject();
            value.x = json.get("x").getAsInt();
            value.y = json.get("y").getAsInt();
            value.width = json.get("width").getAsInt();
            value.height = json.get("height").getAsInt();
            value.folded = json.get("folded").getAsBoolean();
            if (!json.has("layout_version")) {
                // Account for the reduced frame when migrating the original window preferences.
                value.width += 2;
                value.height -= 30;
            }
        } catch (IOException | RuntimeException failure) {
            Data_Energistics.LOGGER.warn("Cannot load player terminal geometry from {}", FILE, failure);
        }
        return value;
    }

    public void save() {
        JsonObject json = new JsonObject();
        json.addProperty("layout_version", 2);
        json.addProperty("x", x);
        json.addProperty("y", y);
        json.addProperty("width", width);
        json.addProperty("height", height);
        json.addProperty("folded", folded);
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, json.toString(), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            Data_Energistics.LOGGER.warn("Cannot save player terminal geometry to {}", FILE, failure);
        }
    }
}
