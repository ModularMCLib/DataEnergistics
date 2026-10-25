package com.fish_dan_.data_energistics.common.effect.reboot;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.neoforge.common.EffectCure;

import java.util.Set;

/** Visible mirror of the world-owned cooldown, rather than the source of its expiry. */
public final class RadixRebootCooldownMobEffect extends MobEffect {

    public RadixRebootCooldownMobEffect() {
        super(MobEffectCategory.NEUTRAL, 0x65EEE9);
    }

    @Override
    public void fillEffectCures(Set<EffectCure> cures, MobEffectInstance effectInstance) {
        cures.clear();
    }
}
