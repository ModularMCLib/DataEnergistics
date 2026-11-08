package com.fish_dan_.data_energistics.config.client.hud.tactical;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import lombok.Builder;

/** Transparent component styling. Ratios describe travel within the viewport after scaling. */
@Builder(toBuilder = true)
public record HudWidgetStyle(double x, double y, float scale, int textColor, int lineColor, int lineWidth, boolean visible) {

    public static final int TEXT = 0xfff2f6f7;
    public static final int LINE = 0xff57e0bb;
    public static final Codec<HudWidgetStyle> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.doubleRange(0, 1).fieldOf("x").forGetter(HudWidgetStyle::x),
            Codec.doubleRange(0, 1).fieldOf("y").forGetter(HudWidgetStyle::y),
            Codec.floatRange(0.5f, 2).fieldOf("scale").forGetter(HudWidgetStyle::scale),
            Codec.INT.fieldOf("text_color").forGetter(HudWidgetStyle::textColor),
            Codec.INT.fieldOf("line_color").forGetter(HudWidgetStyle::lineColor),
            Codec.intRange(0, 4).fieldOf("line_width").forGetter(HudWidgetStyle::lineWidth),
            Codec.BOOL.optionalFieldOf("visible", true).forGetter(HudWidgetStyle::visible))
            .apply(instance, HudWidgetStyle::new));

    public static HudWidgetStyle defaults(TacticalHudWidget widget) {
        var builder = builder().scale(1).textColor(TEXT).lineColor(LINE).lineWidth(1).visible(true);
        return switch (widget) {
            case LEFT_WEAPONS -> builder.x(0.015).y(0.5).build();
            case RIGHT_WEAPONS -> builder.x(0.985).y(0.5).build();
            case REAL_TIME -> builder.x(0.38).y(0.012).build();
            case GAME_TIME -> builder.x(0.5).y(0.012).build();
            case SESSION_TIME -> builder.x(0.62).y(0.012).build();
            case CONNECTION -> builder.x(0.015).y(0.075).build();
            case ENERGY -> builder.x(0.015).y(0.11).build();
            case FLOW -> builder.x(0.015).y(0.145).build();
            case COORDINATES -> builder.x(0.5).y(0.155).build();
        };
    }
}
