package com.fish_dan_.data_energistics.mixin.minecraft.network;

import com.fish_dan_.data_energistics.common.combat.erasure.lifecycle.ErasureAttachments;
import com.fish_dan_.data_energistics.common.data.DEAttachments;
import com.fish_dan_.data_energistics.common.weapon.platform.shield.PlatformShield;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SyncedDataHolder;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** Covers native synced health writes without repeating shield payment from the ordinary damage path. */
@Mixin(SynchedEntityData.class)
abstract class PlatformShieldSyncedHealthMixin {

    @Shadow
    @Final
    private SyncedDataHolder entity;

    @WrapMethod(method = "set(Lnet/minecraft/network/syncher/EntityDataAccessor;Ljava/lang/Object;Z)V")
    private <T> void dataEnergistics$healthData(EntityDataAccessor<T> accessor, T value, boolean force, Operation<Void> original) {
        if (accessor != LivingEntity.DATA_HEALTH_ID || !(entity instanceof ServerPlayer player) || ErasureAttachments.blocksRecovery(player)) {
            original.call(accessor, value, force);
            return;
        }
        var writes = player.getData(DEAttachments.SHIELD_HEALTH_WRITES);
        float requested = (Float) value, current = player.getHealth();
        if (writes.settled != 0 || requested >= current || current <= 0) {
            original.call(accessor, value, force);
            return;
        }
        float adjusted = (float) (current - PlatformShield.absorb(player, (double) current - requested, writes.sourceAt == player.level().getGameTime() ? writes.source : null));
        if (requested <= 0 && adjusted > 0) writes.rejectedDeathAt = player.level().getGameTime();
        original.call(accessor, adjusted, force);
    }
}
