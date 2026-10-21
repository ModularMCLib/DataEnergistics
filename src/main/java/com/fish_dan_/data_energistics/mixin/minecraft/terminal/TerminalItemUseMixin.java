package com.fish_dan_.data_energistics.mixin.minecraft.terminal;

import com.fish_dan_.data_energistics.common.terminal.player.session.TerminalSessions;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;

/** Observes actual use deltas without changing the stack supplied to vanilla or third-party item logic. */
@Mixin(ServerPlayerGameMode.class)
public abstract class TerminalItemUseMixin {

    @WrapMethod(method = "useItemOn")
    private InteractionResult dataEnergistics$payBlockUse(ServerPlayer player, Level level, ItemStack stack, InteractionHand hand,
                                                        BlockHitResult hit, Operation<InteractionResult> original) {
        var use = TerminalSessions.beginItemUse(player);
        InteractionResult result = original.call(player, level, stack, hand, hit);
        TerminalSessions.finishItemUse(player, use);
        return result;
    }

    @WrapMethod(method = "useItem")
    private InteractionResult dataEnergistics$payItemUse(ServerPlayer player, Level level, ItemStack stack, InteractionHand hand,
                                                       Operation<InteractionResult> original) {
        var use = TerminalSessions.beginItemUse(player);
        InteractionResult result = original.call(player, level, stack, hand);
        TerminalSessions.finishItemUse(player, use);
        return result;
    }
}
