package com.fish_dan_.data_energistics.client.registry.item;

import com.fish_dan_.data_energistics.client.render.item.sanctifier.animation.SanctifierAnimationStates;
import com.fish_dan_.data_energistics.common.data.DEEnumProxies;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel.ArmPose;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

import org.jspecify.annotations.Nullable;

/** The draw pose owns both hands only while the support hand is available. */
public final class SanctifierClientExtensions implements IClientItemExtensions {

    @Override
    public @Nullable ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
        float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
        return hand == InteractionHand.MAIN_HAND && SanctifierAnimationStates.usesSupportHand(entity, partial) ? DEEnumProxies.SANCTIFIER_DRAW.getValue() : null;
    }
}
