package com.fish_dan_.data_energistics.config.client.hud;

import com.fish_dan_.data_energistics.common.weapon.platform.model.HudModule;
import com.fish_dan_.data_energistics.common.weapon.platform.ui.WeaponPlatformStyle;

import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMaps;

import java.util.Locale;

/** Complete local appearance snapshot. Missing module entries receive their documented default style. */
public record TacticalHudAppearance(Reference2ObjectMap<HudModule, HudModuleStyle> styles) {

    public static final TacticalHudAppearance DEFAULT = new TacticalHudAppearance(Reference2ObjectMaps.emptyMap());
    public static final Codec<TacticalHudAppearance> CODEC = Codec.unboundedMap(Codec.STRING, HudModuleStyle.CODEC).xmap(values -> {
        Reference2ObjectMap<HudModule, HudModuleStyle> styles = new Reference2ObjectLinkedOpenHashMap<>();
        for (HudModule module : HudModule.values()) {
            HudModuleStyle style = values.get(module.name().toLowerCase(Locale.ROOT));
            if (style != null) styles.put(module, style);
        }
        return new TacticalHudAppearance(styles);
    }, appearance -> {
        var styles = new Object2ObjectLinkedOpenHashMap<String, HudModuleStyle>(appearance.styles.size());
        appearance.styles.forEach((module, style) -> styles.put(module.name().toLowerCase(Locale.ROOT), style));
        return styles;
    });

    public TacticalHudAppearance {
        Reference2ObjectMap<HudModule, HudModuleStyle> complete = new Reference2ObjectLinkedOpenHashMap<>();
        for (HudModule module : HudModule.values()) complete.put(module, styles.getOrDefault(module, defaults(module)));
        styles = Reference2ObjectMaps.unmodifiable(complete);
    }

    public static HudModuleStyle defaults(HudModule module) {
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
            case ORES -> style.x(0.5).y(0.55).background(0x609a9fb4).borderWidth(1).fields(HudModuleStyle.NAME | HudModuleStyle.DISTANCE).build();
        };
    }
}
