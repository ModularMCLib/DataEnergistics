package com.fish_dan_.data_energistics.config.client.hud.tactical;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** The two independently positioned weapon columns, mirrored around the central view. */
@Getter
@RequiredArgsConstructor
public enum WeaponHudSide {

    LEFT(TacticalHudWidget.LEFT_WEAPONS),
    RIGHT(TacticalHudWidget.RIGHT_WEAPONS);

    private final TacticalHudWidget widget;
}
