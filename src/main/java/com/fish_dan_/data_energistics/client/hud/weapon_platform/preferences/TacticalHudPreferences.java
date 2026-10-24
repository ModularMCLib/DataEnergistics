package com.fish_dan_.data_energistics.client.hud.weapon_platform.preferences;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.weapon.platform.ui.WeaponPlatformStyle;

import net.neoforged.fml.loading.FMLPaths;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Locale;

/** Local visual preferences. Codecs decode the fixed record schema; no reflective object serialization. */
public final class TacticalHudPreferences {

    private static final Path FILE = FMLPaths.CONFIGDIR.get().resolve(Data_Energistics.MODID).resolve("tactical_hud.json");
    private static final Codec<EnumMap<HudModule, HudModuleStyle>> CODEC = Codec.unboundedMap(Codec.STRING, HudModuleStyle.CODEC).xmap(values -> {
        var styles = new EnumMap<HudModule, HudModuleStyle>(HudModule.class);
        for (HudModule module : HudModule.values()) {
            HudModuleStyle style = values.get(module.name().toLowerCase(Locale.ROOT));
            if (style != null) styles.put(module, style);
        }
        return styles;
    }, values -> {
        var styles = new Object2ObjectOpenHashMap<String, HudModuleStyle>(values.size());
        values.forEach((module, style) -> styles.put(module.name().toLowerCase(Locale.ROOT), style));
        return styles;
    });
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

    public static void resetAppearance(HudModule module) {
        HudModuleStyle current = get(module);
        set(module, defaults(module).toBuilder().visible(current.visible()).fields(current.fields()).build());
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
        var style = HudModuleStyle.builder().scale(1).textColor(WeaponPlatformStyle.TEXT)
                .borderColor(WeaponPlatformStyle.EDGE).visible(true);
        return switch (module) {
            case COMPASS -> style.x(0.5).y(0.015).textColor(WeaponPlatformStyle.EDGE).fields(7).build();
            case WEAPONS -> style.x(0.015).y(0.65).background(WeaponPlatformStyle.HUD_INFORMATION_BACKGROUND)
                    .borderColor(WeaponPlatformStyle.HUD_INFORMATION_EDGE).borderWidth(1).fields(49).build();
            case NETWORK -> style.x(0.015).y(0.14).background(WeaponPlatformStyle.HUD_INFORMATION_BACKGROUND)
                    .borderColor(WeaponPlatformStyle.HUD_INFORMATION_EDGE).borderWidth(1).fields(7).build();
            case TARGETS -> style.background(0xb0101e26).borderWidth(1).relationColors(true)
                    .fields(HudModuleStyle.NAME | HudModuleStyle.DISTANCE | HudModuleStyle.HEALTH | HudModuleStyle.BINARY | HudModuleStyle.TARGET | HudModuleStyle.SPEED | HudModuleStyle.ALTITUDE | HudModuleStyle.MOTION).build();
            case GUIDES -> style.background(0xb0e3e3ea).borderWidth(1).fields(7).build();
        };
    }
}
