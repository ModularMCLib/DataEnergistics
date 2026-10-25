package com.fish_dan_.data_energistics.client.hud.weapon_platform.rendering;

import com.fish_dan_.data_energistics.client.hud.weapon_platform.preferences.HudModule;
import com.fish_dan_.data_energistics.client.hud.weapon_platform.preferences.TacticalHudPreferences;
import com.fish_dan_.data_energistics.client.render.overlay.PlatformOreRenderer;

import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;

public final class TacticalOreLabel extends Label {

    public TacticalOreLabel() {
        addClass("platform-hud-label");
        layout(style -> style.width(200).height(18));
        addEventListener(UIEvents.TICK, event -> {
            var value = PlatformOreRenderer.label();
            setValue(value);
            textStyle(style -> style.textColor(TacticalHudPreferences.get(HudModule.ORES).textColor()));
        });
    }
}
