package com.fish_dan_.data_energistics.common.weapon.platform.model;

import net.minecraft.util.StringRepresentable;

import com.mojang.serialization.Codec;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Stable saved names for the platform's selectable thermal display. */
@RequiredArgsConstructor
public enum ThermalPalette implements StringRepresentable {

    WHITE_HOT("white_hot"),
    FALSE_COLOR("false_color"),
    OVERLAY("overlay");

    public static final Codec<ThermalPalette> CODEC = StringRepresentable.fromEnum(ThermalPalette::values);

    @Getter
    private final String serializedName;
}
