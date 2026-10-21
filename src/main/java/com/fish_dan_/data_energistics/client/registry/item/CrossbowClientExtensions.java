package com.fish_dan_.data_energistics.client.registry.item;

import com.fish_dan_.data_energistics.common.data.DEEnumProxies;
import com.fish_dan_.data_energistics.common.item.powered.MatterConvergingCrossbowItem;
import com.fish_dan_.data_energistics.common.item.powered.MatterConvergingCrossbowMode;

import net.minecraft.client.model.HumanoidModel.ArmPose;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

import org.jspecify.annotations.Nullable;

/** Keeps cannon holding on the native arm-pose path without replacing unrelated use animations. */
public final class CrossbowClientExtensions implements IClientItemExtensions {

    @Override
    public @Nullable ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
        if (entity.isUsingItem()) {
            ItemStack using = entity.getUseItem();
            if (!(using.getItem() instanceof MatterConvergingCrossbowItem) || MatterConvergingCrossbowItem.mode(using) == MatterConvergingCrossbowMode.CROSSBOW) return null;
        }
        ItemStack other = entity.getItemInHand(hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
        return (other.isEmpty() ? DEEnumProxies.CROSSBOW_HOLD : DEEnumProxies.CROSSBOW_HOLD_SINGLE).getValue();
    }
}
