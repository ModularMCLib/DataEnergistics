package com.fish_dan_.data_energistics.client.hud.weapon_platform.preferences;

import com.fish_dan_.data_energistics.client.weapon.platform.PlatformClientState;
import com.fish_dan_.data_energistics.common.weapon.platform.model.HudModule;
import com.fish_dan_.data_energistics.common.weapon.platform.storage.PlatformHudDisplay;
import com.fish_dan_.data_energistics.config.client.ClientConfigurations;
import com.fish_dan_.data_energistics.config.client.hud.HudModuleStyle;
import com.fish_dan_.data_energistics.config.client.hud.TacticalHudAppearance;
import com.fish_dan_.data_energistics.config.client.hud.tactical.HudWidgetStyle;
import com.fish_dan_.data_energistics.config.client.hud.tactical.TacticalHudWidget;
import com.fish_dan_.data_energistics.config.client.hud.tactical.WeaponHudLayout;

import lombok.Getter;

import java.util.EnumMap;

/** Local visual preferences. Codecs decode the fixed record schema; no reflective object serialization. */
public final class TacticalHudPreferences {

    private static final EnumMap<HudModule, HudModuleStyle> EFFECTIVE = new EnumMap<>(HudModule.class);
    private static PlatformHudDisplay displayed = PlatformHudDisplay.DEFAULT;
    @Getter
    private static long revision;

    private TacticalHudPreferences() {}

    public static HudModuleStyle get(HudModule module) {
        var settings = PlatformClientState.snapshot.systems().hud();
        if (!displayed.equals(settings)) {
            displayed = settings;
            EFFECTIVE.clear();
        }
        return EFFECTIVE.computeIfAbsent(module, (HudModule entry) -> ClientConfigurations.TACTICAL_HUD.get().styles().get(entry).toBuilder().visible(displayed.visible(entry)).fields(displayed.fields().getInt(entry.ordinal())).build());
    }

    public static void set(HudModule module, HudModuleStyle style) {
        if (!ClientConfigurations.TACTICAL_HUD.save(appearance().withModule(module, style))) return;
        EFFECTIVE.remove(module);
        revision++;
    }

    public static void resetAppearance(HudModule module) {
        set(module, TacticalHudAppearance.defaults(module));
    }

    public static TacticalHudAppearance appearance() {
        return ClientConfigurations.TACTICAL_HUD.get();
    }

    public static HudWidgetStyle get(TacticalHudWidget widget) {
        return appearance().widgets().get(widget);
    }

    public static boolean enabled(TacticalHudWidget widget) {
        if (!get(widget).visible()) return false;
        return switch (widget) {
            case LEFT_WEAPONS, RIGHT_WEAPONS -> get(HudModule.WEAPONS).visible();
            case CONNECTION -> get(HudModule.NETWORK).visible() && get(HudModule.NETWORK).shows(HudModuleStyle.CONNECTION);
            case ENERGY -> get(HudModule.NETWORK).visible() && get(HudModule.NETWORK).shows(HudModuleStyle.ENERGY);
            case FLOW -> get(HudModule.NETWORK).visible() && get(HudModule.NETWORK).shows(HudModuleStyle.FLOW);
            default -> true;
        };
    }

    public static void set(TacticalHudWidget widget, HudWidgetStyle style) {
        save(appearance().withWidget(widget, style));
    }

    public static void setWeapons(WeaponHudLayout layout) {
        save(appearance().withWeapons(layout));
    }

    public static void resetAppearance(TacticalHudWidget widget) {
        set(widget, HudWidgetStyle.defaults(widget));
    }

    private static void save(TacticalHudAppearance candidate) {
        if (ClientConfigurations.TACTICAL_HUD.save(candidate)) revision++;
    }
}
