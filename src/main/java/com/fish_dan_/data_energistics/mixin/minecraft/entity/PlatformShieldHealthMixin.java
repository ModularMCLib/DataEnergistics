package com.fish_dan_.data_energistics.mixin.minecraft.entity;

import com.fish_dan_.data_energistics.common.combat.erasure.lifecycle.ErasureAttachments;
import com.fish_dan_.data_energistics.common.data.DEAttachments;
import com.fish_dan_.data_energistics.common.weapon.platform.shield.PlatformShield;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;

/** Direct health setters retain the original damage amount before vanilla clamps it to zero. */
@Mixin(LivingEntity.class)
abstract class PlatformShieldHealthMixin {

    @WrapMethod(method = "setHealth")
    private void dataEnergistics$directHealth(float requested, Operation<Void> original) {
        if (!((Object) this instanceof ServerPlayer player) || ErasureAttachments.blocksRecovery(player)) {
            original.call(requested);
            return;
        }
        var writes = player.getData(DEAttachments.SHIELD_HEALTH_WRITES);
        float current = player.getHealth();
        if (writes.settled != 0 || requested >= current || current <= 0) {
            original.call(requested);
            return;
        }
        double remaining = PlatformShield.absorb(player, (double) current - requested);
        float adjusted = (float) (current - remaining);
        if (requested <= 0 && adjusted > 0) writes.rejectedDeathAt = player.level().getGameTime();
        writes.settled++;
        try {
            original.call(adjusted);
        } finally {
            writes.settled--;
        }
    }
}
