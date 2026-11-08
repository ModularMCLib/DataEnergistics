package com.fish_dan_.data_energistics.config.client.hud;

import net.minecraft.util.StringRepresentable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Client-local placement and presentation choices. All external and edited values normalize at construction. */
public record OrbitalHudPreferences(Anchor anchor, int offsetX, int offsetY, float scale, float opacity, DisplayMode mode) {

    public static final OrbitalHudPreferences DEFAULT = new OrbitalHudPreferences(Anchor.TOP_LEFT, 8, 8, 1.0F, 1.0F, DisplayMode.COMPACT);
    public static final Codec<OrbitalHudPreferences> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Anchor.CODEC.optionalFieldOf("anchor", Anchor.TOP_LEFT).forGetter(OrbitalHudPreferences::anchor),
            Codec.INT.optionalFieldOf("offsetX", 8).forGetter(OrbitalHudPreferences::offsetX),
            Codec.INT.optionalFieldOf("offsetY", 8).forGetter(OrbitalHudPreferences::offsetY),
            Codec.FLOAT.optionalFieldOf("scale", 1.0F).forGetter(OrbitalHudPreferences::scale),
            Codec.FLOAT.optionalFieldOf("opacity", 1.0F).forGetter(OrbitalHudPreferences::opacity),
            DisplayMode.CODEC.optionalFieldOf("mode", DisplayMode.COMPACT).forGetter(OrbitalHudPreferences::mode))
            .apply(instance, OrbitalHudPreferences::new));

    public OrbitalHudPreferences {
        offsetX = Math.clamp(offsetX, 0, 4096);
        offsetY = Math.clamp(offsetY, 0, 4096);
        scale = Float.isFinite(scale) ? Math.clamp(scale, 0.75F, 1.75F) : 1.0F;
        opacity = Float.isFinite(opacity) ? Math.clamp(opacity, 0.2F, 1.0F) : 1.0F;
    }

    public enum Anchor implements StringRepresentable {

        TOP_LEFT,
        TOP_RIGHT,
        BOTTOM_LEFT,
        BOTTOM_RIGHT;

        public static final Codec<Anchor> CODEC = StringRepresentable.fromEnum(Anchor::values);

        /** Retains the uppercase names already stored in existing HUD preferences. */
        @Override
        public String getSerializedName() {
            return name();
        }
    }

    public enum DisplayMode implements StringRepresentable {

        COMPACT,
        DETAIL,
        HIDDEN;

        public static final Codec<DisplayMode> CODEC = StringRepresentable.fromEnum(DisplayMode::values);

        @Override
        public String getSerializedName() {
            return name();
        }
    }
}
