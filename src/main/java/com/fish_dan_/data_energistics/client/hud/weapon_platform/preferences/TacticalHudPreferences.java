package com.fish_dan_.data_energistics.client.hud.weapon_platform.preferences;

import com.fish_dan_.data_energistics.client.weapon.platform.PlatformClientState;
import com.fish_dan_.data_energistics.common.weapon.platform.model.HudModule;
import com.fish_dan_.data_energistics.common.weapon.platform.storage.PlatformHudDisplay;
import com.fish_dan_.data_energistics.config.client.ClientConfigurations;
import com.fish_dan_.data_energistics.config.client.hud.HudModuleStyle;
import com.fish_dan_.data_energistics.config.client.hud.TacticalHudAppearance;

import it.unimi.dsi.fastutil.objects.Reference2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import lombok.Getter;

/** Local visual preferences. Codecs decode the fixed record schema; no reflective object serialization. */
public final class TacticalHudPreferences {

    private static final Reference2ObjectMap<HudModule, HudModuleStyle> EFFECTIVE = new Reference2ObjectLinkedOpenHashMap<>();
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
        var styles = new Reference2ObjectLinkedOpenHashMap<>(ClientConfigurations.TACTICAL_HUD.get().styles());
        styles.put(module, style);
        if (!ClientConfigurations.TACTICAL_HUD.save(new TacticalHudAppearance(styles))) return;
        EFFECTIVE.remove(module);
        revision++;
    }

    public static void resetAppearance(HudModule module) {
        set(module, TacticalHudAppearance.defaults(module));
    }
}
