package com.fish_dan_.data_energistics.client.hud.weapon_platform.preferences;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Positions are viewport ratios; scale affects this module alone and background includes its own alpha. */
public record HudModuleStyle(double x, double y, float scale, int background, int fields, boolean visible) {

    public static final int NAME = 1, DISTANCE = 2, HEALTH = 4, BINARY = 8, HEAT = 16, AMMUNITION = 32, TARGET = 64;
    public static final Codec<HudModuleStyle> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.doubleRange(0, 1).fieldOf("x").forGetter(HudModuleStyle::x),
            Codec.doubleRange(0, 1).fieldOf("y").forGetter(HudModuleStyle::y),
            Codec.floatRange(0.5f, 2).fieldOf("scale").forGetter(HudModuleStyle::scale),
            Codec.INT.fieldOf("background").forGetter(HudModuleStyle::background),
            Codec.intRange(0, 127).fieldOf("fields").forGetter(HudModuleStyle::fields),
            Codec.BOOL.fieldOf("visible").forGetter(HudModuleStyle::visible)).apply(instance, HudModuleStyle::new));

    public boolean shows(int field) {
        return (fields & field) != 0;
    }
}
