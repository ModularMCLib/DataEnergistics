package com.fish_dan_.data_energistics.item.powered.cannon.presentation;

import com.fish_dan_.data_energistics.item.powered.MatterConvergingCrossbowMode;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import org.jspecify.annotations.NullMarked;

/** Shared, side-independent translated names for the weapon, its configuration screen and mode tooltips. */
@NullMarked
public final class DigitizedWeaponName {

    private DigitizedWeaponName() {}

    /** Returns a fresh prefix; its color and bold style must not propagate to the separator. */
    public static MutableComponent prefix() {
        return Component.translatable("item.data_energistics.star_shard.display.prefix")
                .withStyle(style -> style.withColor(0x93FFDE).withBold(true));
    }

    /** Returns the localized mode name with its exact RGB color, without changing persistent mode IDs. */
    public static MutableComponent modeName(MatterConvergingCrossbowMode mode) {
        int color = switch (mode) {
            case GRENADE -> 0x8ABBEF;
            case RAIL -> 0x39D6BC;
            case CROSSBOW -> 0x6054A6;
        };
        return Component.translatable("item.data_energistics.star_shard.display.mode." + mode.nameKey())
                .withStyle(style -> style.withColor(color));
    }

    /** Unstyled translation root keeps the separator in the caller's default color. */
    public static MutableComponent fullName(MatterConvergingCrossbowMode mode) {
        return Component.translatable("item.data_energistics.star_shard.display.name", prefix(), modeName(mode));
    }
}
