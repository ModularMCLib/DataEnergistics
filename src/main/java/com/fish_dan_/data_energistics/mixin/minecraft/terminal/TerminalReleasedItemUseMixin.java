package com.fish_dan_.data_energistics.mixin.minecraft.terminal;

import com.fish_dan_.data_energistics.common.terminal.player.session.TerminalSessions;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;

/** Covers ammunition consumed when releasing a bow or charging a crossbow. */
@Mixin(LivingEntity.class)
public abstract class TerminalReleasedItemUseMixin {

    @WrapMethod(method = "releaseUsingItem")
    private void dataEnergistics$payReleasedUse(Operation<Void> original) {
        LivingEntity entity = (LivingEntity) (Object) this;
        if (!(entity instanceof ServerPlayer player)) {
            original.call();
            return;
        }
        var use = TerminalSessions.beginItemUse(player);
        original.call();
        TerminalSessions.finishItemUse(player, use);
    }
}
