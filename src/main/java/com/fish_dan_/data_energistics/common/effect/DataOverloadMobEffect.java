package com.fish_dan_.data_energistics.common.effect;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

public final class DataOverloadMobEffect extends MobEffect {

    public DataOverloadMobEffect() {
        super(MobEffectCategory.BENEFICIAL, 0x21E6DE);
        addAttributeModifier(Attributes.ATTACK_SPEED, Data_Energistics.id("data_overload"),
                0.01, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }
}
