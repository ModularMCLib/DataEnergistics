package com.fish_dan_.data_energistics.common.effect;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Outline color and absolute world expiry; rendering reads this without scoreboard changes. */
public record ChromaticGlowState(int color, long until) {

    public static final ChromaticGlowState EMPTY = new ChromaticGlowState(-1, 0);
    public static final Codec<ChromaticGlowState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("color").forGetter(ChromaticGlowState::color),
            Codec.LONG.fieldOf("until").forGetter(ChromaticGlowState::until)).apply(instance, ChromaticGlowState::new));
}
