package com.fish_dan_.data_energistics.common.effect;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

public final class LifeReductionMobEffect extends MobEffect {

    public LifeReductionMobEffect() {
        super(MobEffectCategory.HARMFUL, 0x9F101A);
        addAttributeModifier(Attributes.MAX_HEALTH, Data_Energistics.id("life_reduction"),
                -0.05, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }

    @Override
    public void addAttributeModifiers(AttributeMap attributes, int amplifier) {
        super.addAttributeModifiers(attributes, Math.min(4, amplifier));
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return true;
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (entity.getHealth() > entity.getMaxHealth()) entity.setHealth(entity.getMaxHealth());
        return true;
    }
}
