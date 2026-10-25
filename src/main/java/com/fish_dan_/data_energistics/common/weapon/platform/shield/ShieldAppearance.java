package com.fish_dan_.data_energistics.common.weapon.platform.shield;

import net.minecraft.util.StringRepresentable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import lombok.Builder;

import java.util.Locale;

/** Persistent shield cosmetics belong to the platform and synchronize to tracking players. */
@Builder(toBuilder = true)
public record ShieldAppearance(int color, int borderColor, int borderWidth, Display display) {

    public static final ShieldAppearance DEFAULT = new ShieldAppearance(0x99696d88, 0x99696d88, 2, Display.ALWAYS);
    public static final Codec<ShieldAppearance> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("color").forGetter(ShieldAppearance::color),
            Codec.INT.fieldOf("border_color").forGetter(ShieldAppearance::borderColor),
            Codec.intRange(0, 4).fieldOf("border_width").forGetter(ShieldAppearance::borderWidth),
            StringRepresentable.fromEnum(Display::values).fieldOf("display").forGetter(ShieldAppearance::display)).apply(instance, ShieldAppearance::new));

    public enum Display implements StringRepresentable {

        IMPACT,
        ALWAYS,
        HIDDEN;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }
}
