package com.fish_dan_.data_energistics.client.terminal;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.neoforged.fml.loading.FMLPaths;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.jspecify.annotations.NullMarked;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Local geometry and display preferences; inventories, ownership and network settings remain server-authoritative. */
@NullMarked
public final class TerminalClientPreferences {

    private static final Path FILE = FMLPaths.CONFIGDIR.get().resolve("data_energistics/player_terminal.json");
    public float xPercent = 0.047058824F;
    public float yPercent = 0.11111111F;
    public int width = TerminalViewportLayout.MIN_WIDTH;
    public int height = 168;
    public boolean folded = true;
    public boolean open = true;
    public boolean shiftClickToNetwork;
    public String sort = "name";
    public String filter = "all";
    public boolean descending;
    public final ObjectOpenHashSet<String> disabledKeyTypes = new ObjectOpenHashSet<>();

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
            if (json.has("shift_click_to_network")) value.shiftClickToNetwork = json.get("shift_click_to_network").getAsBoolean();
            if (json.has("sort")) value.sort = json.get("sort").getAsString();
            if (json.has("filter")) value.filter = json.get("filter").getAsString();
            if (json.has("descending")) value.descending = json.get("descending").getAsBoolean();
            if (json.has("disabled_key_types")) {
                for (var type : json.getAsJsonArray("disabled_key_types")) value.disabledKeyTypes.add(type.getAsString());
            }
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
        json.addProperty("shift_click_to_network", shiftClickToNetwork);
        json.addProperty("sort", sort);
        json.addProperty("filter", filter);
        json.addProperty("descending", descending);
        JsonArray disabledTypes = new JsonArray();
        disabledKeyTypes.stream().sorted().forEach(disabledTypes::add);
        json.add("disabled_key_types", disabledTypes);
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, json.toString(), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            Data_Energistics.LOGGER.warn("Cannot save player terminal geometry to {}", FILE, failure);
        }
    }
}
