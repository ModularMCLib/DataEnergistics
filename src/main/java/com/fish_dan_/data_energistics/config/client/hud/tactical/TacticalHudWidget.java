package com.fish_dan_.data_energistics.config.client.hud.tactical;

import com.fish_dan_.data_energistics.common.weapon.platform.model.HudModule;

import net.minecraft.util.StringRepresentable;

import com.mojang.serialization.Codec;

import java.util.Locale;

/** Client-only layout identities; these never change the shared HUD module ordinals. */
public enum TacticalHudWidget implements StringRepresentable {

    LEFT_WEAPONS,
    RIGHT_WEAPONS,
    REAL_TIME,
    GAME_TIME,
    SESSION_TIME,
    CONNECTION,
    ENERGY,
    FLOW,
    COORDINATES;

    public static final Codec<TacticalHudWidget> CODEC = StringRepresentable.fromEnum(TacticalHudWidget::values);

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String translationKey() {
        return "hud.data_energistics.weapon_platform.widget." + getSerializedName();
    }

    public HudModule appearanceSource() {
        return switch (this) {
            case LEFT_WEAPONS, RIGHT_WEAPONS -> HudModule.WEAPONS;
            case CONNECTION, ENERGY, FLOW -> HudModule.NETWORK;
            default -> HudModule.COMPASS;
        };
    }

    public boolean weapons() {
        return this == LEFT_WEAPONS || this == RIGHT_WEAPONS;
    }

    public boolean fixedToHeader() {
        return switch (this) {
            case REAL_TIME, GAME_TIME, SESSION_TIME, COORDINATES -> true;
            default -> false;
        };
    }
}
