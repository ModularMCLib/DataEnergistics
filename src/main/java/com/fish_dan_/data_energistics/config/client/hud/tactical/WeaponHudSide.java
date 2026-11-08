package com.fish_dan_.data_energistics.config.client.hud.tactical;

/** The two independently positioned weapon columns, mirrored around the central view. */
public enum WeaponHudSide {

    LEFT(TacticalHudWidget.LEFT_WEAPONS),
    RIGHT(TacticalHudWidget.RIGHT_WEAPONS);

    public final TacticalHudWidget widget;

    WeaponHudSide(TacticalHudWidget widget) {
        this.widget = widget;
    }
}
