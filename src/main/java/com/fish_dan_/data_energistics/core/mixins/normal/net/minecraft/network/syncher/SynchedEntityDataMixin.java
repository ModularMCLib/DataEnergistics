package com.fish_dan_.data_energistics.core.mixins.normal.net.minecraft.network.syncher;

import com.fish_dan_.data_energistics.common.combat.erasure.lifecycle.ErasureAttachments;
import com.fish_dan_.data_energistics.common.combat.survival.PlayerSurvival;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SyncedDataHolder;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.jspecify.annotations.NullMarked;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** Covers native synced health writes without repeating shield payment from the ordinary damage path. */
@Mixin(SynchedEntityData.class)
@NullMarked
public abstract class SynchedEntityDataMixin {

    @Shadow
    @Final
    private SyncedDataHolder entity;

    @WrapMethod(method = "set(Lnet/minecraft/network/syncher/EntityDataAccessor;Ljava/lang/Object;Z)V")
    private <T> void dataEnergistics$healthData(EntityDataAccessor<T> accessor, T value, boolean force, Operation<Void> original) {
        if (accessor != LivingEntity.DATA_HEALTH_ID || !(entity instanceof ServerPlayer player) || ErasureAttachments.blocksRecovery(player)) {
            original.call(accessor, value, force);
            return;
        }
        PlayerSurvival.writeHealth(player, (Float) value, false, adjusted -> original.call(accessor, adjusted, force));
    }
}
