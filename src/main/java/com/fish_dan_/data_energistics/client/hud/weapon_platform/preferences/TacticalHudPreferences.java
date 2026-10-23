package com.fish_dan_.data_energistics.client.hud.weapon_platform.preferences;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.neoforged.fml.loading.FMLPaths;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Locale;

/** Local visual preferences. Codecs decode the fixed record schema; no reflective object serialization. */
public final class TacticalHudPreferences {

    private static final Path FILE = FMLPaths.CONFIGDIR.get().resolve("data_energistics_tactical_hud.json");
    private static final Codec<HudModule> MODULE_CODEC = Codec.STRING.xmap(value -> HudModule.valueOf(value.toUpperCase(Locale.ROOT)), value -> value.name().toLowerCase(Locale.ROOT));
    private static final Codec<EnumMap<HudModule, HudModuleStyle>> CODEC = Codec.unboundedMap(MODULE_CODEC, HudModuleStyle.CODEC).xmap(values -> {
        var styles = new EnumMap<HudModule, HudModuleStyle>(HudModule.class);
        styles.putAll(values);
        return styles;
    }, values -> values);
    private static final EnumMap<HudModule, HudModuleStyle> STYLES = load();
    private static long revision;

    private TacticalHudPreferences() {}

    public static HudModuleStyle get(HudModule module) {
        return STYLES.get(module);
    }

    public static long revision() {
        return revision;
    }

    public static void set(HudModule module, HudModuleStyle style) {
        STYLES.put(module, style);
        revision++;
        try {
            Files.createDirectories(FILE.getParent());
            var json = CODEC.encodeStart(JsonOps.INSTANCE, STYLES).getOrThrow();
            Files.writeString(FILE, new GsonBuilder().setPrettyPrinting().create().toJson(json) + "\n", StandardCharsets.UTF_8);
        } catch (IOException failure) {
            Data_Energistics.LOGGER.warn("Unable to save tactical HUD preferences to {}", FILE, failure);
        }
    }

    public static void reset(HudModule module) {
        set(module, defaults(module));
    }

    private static EnumMap<HudModule, HudModuleStyle> load() {
        var styles = new EnumMap<HudModule, HudModuleStyle>(HudModule.class);
        for (HudModule module : HudModule.values()) styles.put(module, defaults(module));
        if (Files.isRegularFile(FILE)) {
            try {
                styles.putAll(CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(Files.readString(FILE, StandardCharsets.UTF_8))).getOrThrow());
            } catch (IOException | RuntimeException failure) {
                Data_Energistics.LOGGER.warn("Unable to load tactical HUD preferences from {}", FILE, failure);
            }
        }
        return styles;
    }

    private static HudModuleStyle defaults(HudModule module) {
        return switch (module) {
            case COMPASS -> new HudModuleStyle(0.5, 0.015, 1, 0, 7, true);
            case WEAPONS -> new HudModuleStyle(0.015, 0.65, 1, 0xb0e3e3ea, 127, true);
            case NETWORK -> new HudModuleStyle(0.015, 0.14, 1, 0xa0e3e3ea, 127, true);
            case TARGETS -> new HudModuleStyle(0, 0, 1, 0xb0e3e3ea, 79, true);
            case CONTROLS -> new HudModuleStyle(0.985, 0.98, 1, 0xf0e3e3ea, 127, true);
            case GUIDES -> new HudModuleStyle(0, 0, 1, 0xb0e3e3ea, 7, true);
        };
    }
}
