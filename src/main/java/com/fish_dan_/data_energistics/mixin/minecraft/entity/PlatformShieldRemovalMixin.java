package com.fish_dan_.data_energistics.mixin.minecraft.entity;

import com.fish_dan_.data_energistics.common.combat.erasure.lifecycle.ErasureAttachments;
import com.fish_dan_.data_energistics.common.data.DEAttachments;
import com.fish_dan_.data_energistics.common.weapon.platform.shield.PlatformShield;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Entity.RemovalReason;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A live player's forced kill-removal uses strong resistance; unloading and dimension changes remain valid. */
@Mixin(Entity.class)
abstract class PlatformShieldRemovalMixin {

    @Inject(method = "setRemoved", at = @At("HEAD"), cancellable = true)
    private void dataEnergistics$forcedRemoval(RemovalReason reason, CallbackInfo ci) {
        if (reason != RemovalReason.KILLED || !((Object) this instanceof ServerPlayer player) || player.getHealth() <= 0 || ErasureAttachments.blocksRecovery(player)) return;
        var writes = player.getData(DEAttachments.SHIELD_HEALTH_WRITES);
        if (writes.rejectedDeathAt == player.level().getGameTime()) ci.cancel();
        else if (PlatformShield.resistDeath(player)) {
            writes.rejectedDeathAt = player.level().getGameTime();
            ci.cancel();
        }
    }
}
