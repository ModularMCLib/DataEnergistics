package com.fish_dan_.data_energistics.client.hud.weapon_platform.preferences;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import lombok.Builder;

/** Positions are viewport ratios; scale affects this module alone and background includes its own alpha. */
@Builder(toBuilder = true)
public record HudModuleStyle(double x, double y, float scale, int background, int textColor, int borderColor,
                             int borderWidth, boolean relationColors, int fields, boolean visible) {

    public static final int NAME = 1, DISTANCE = 2, HEALTH = 4, BINARY = 8, HEAT = 16, AMMUNITION = 32, TARGET = 64;
    public static final int LASER = 1, TRAJECTORY = 2, IMPACT = 4;
    public static final int DIRECTIONS = 1, BEARING = 2, TICKS = 4;
    public static final int CONNECTION = 1, ENERGY = 2, FLOW = 4;
    public static final int SPEED = 128, ALTITUDE = 256, MOTION = 512;
    private static final int DISPLAY_FIELDS = 1023;
    public static final Codec<HudModuleStyle> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.doubleRange(0, 1).fieldOf("x").forGetter(HudModuleStyle::x),
            Codec.doubleRange(0, 1).fieldOf("y").forGetter(HudModuleStyle::y),
            Codec.floatRange(0.5f, 2).fieldOf("scale").forGetter(HudModuleStyle::scale),
            Codec.INT.fieldOf("background").forGetter(HudModuleStyle::background),
            Codec.INT.fieldOf("text_color").forGetter(HudModuleStyle::textColor),
            Codec.INT.fieldOf("border_color").forGetter(HudModuleStyle::borderColor),
            Codec.intRange(0, 4).fieldOf("border_width").forGetter(HudModuleStyle::borderWidth),
            Codec.BOOL.fieldOf("relation_colors").forGetter(HudModuleStyle::relationColors),
            Codec.intRange(0, DISPLAY_FIELDS).fieldOf("fields").forGetter(HudModuleStyle::fields),
            Codec.BOOL.fieldOf("visible").forGetter(HudModuleStyle::visible)).apply(instance, HudModuleStyle::new));

    public boolean shows(int field) {
        return (fields & field) != 0;
    }
}
