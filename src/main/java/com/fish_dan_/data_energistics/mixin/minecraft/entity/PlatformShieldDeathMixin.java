package com.fish_dan_.data_energistics.mixin.minecraft.entity;

import com.fish_dan_.data_energistics.common.combat.erasure.lifecycle.ErasureAttachments;
import com.fish_dan_.data_energistics.common.data.DEAttachments;
import com.fish_dan_.data_energistics.common.weapon.platform.shield.PlatformShield;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Last boundary for direct death calls that never entered hurt or setHealth. */
@Mixin(ServerPlayer.class)
abstract class PlatformShieldDeathMixin {

    @Inject(method = "die", at = @At("HEAD"), cancellable = true)
    private void dataEnergistics$directDeath(DamageSource source, CallbackInfo ci) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        if (ErasureAttachments.blocksRecovery(player)) return;
        var writes = player.getData(DEAttachments.SHIELD_HEALTH_WRITES);
        if (writes.settled != 0) return;
        if (writes.rejectedDeathAt == player.level().getGameTime() && player.getHealth() > 0) {
            ci.cancel();
        } else if (PlatformShield.resistDeath(player)) {
            writes.rejectedDeathAt = player.level().getGameTime();
            if (player.getHealth() <= 0) player.setHealth(1);
            ci.cancel();
        }
    }
}
