package com.fish_dan_.data_energistics.common.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/** AI suspension is tracked separately from damage stacks and never blocks player input. */
public final class RadixStasisMobEffect extends MobEffect {

    public RadixStasisMobEffect() {
        super(MobEffectCategory.HARMFUL, 0x36D7D1);
    }
}
