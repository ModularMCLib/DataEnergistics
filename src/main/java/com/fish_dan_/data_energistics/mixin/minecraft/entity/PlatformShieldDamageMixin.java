package com.fish_dan_.data_energistics.mixin.minecraft.entity;

import com.fish_dan_.data_energistics.common.data.DEAttachments;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Only the native health write has already paid through the incoming-damage event. */
@Mixin(Player.class)
abstract class PlatformShieldDamageMixin {

    @WrapOperation(method = "actuallyHurt", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;setHealth(F)V"))
    private void dataEnergistics$settledHealth(Player target, float health, Operation<Void> original) {
        if (!(target instanceof ServerPlayer player)) {
            original.call(target, health);
            return;
        }
        var writes = player.getData(DEAttachments.SHIELD_HEALTH_WRITES);
        writes.settled++;
        try {
            original.call(target, health);
        } finally {
            writes.settled--;
        }
    }
}
