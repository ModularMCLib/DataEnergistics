package com.fish_dan_.data_energistics.mixin.minecraft.entity;

import com.fish_dan_.data_energistics.common.weapon.platform.ability.PlatformMotion;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.level.portal.DimensionTransition;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;

@Mixin(ServerPlayer.class)
public abstract class PlatformPlayerTeleportMixin {

    @Inject(method = "changeDimension(Lnet/minecraft/world/level/portal/DimensionTransition;)Lnet/minecraft/world/entity/Entity;", at = @At("HEAD"), cancellable = true)
    private void dataEnergistics$dimension(DimensionTransition transition, CallbackInfoReturnable<Entity> cir) {
        if (PlatformMotion.blocked((Entity) (Object) this)) cir.setReturnValue(null);
    }

    @Inject(method = "teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDFF)V", at = @At("HEAD"), cancellable = true)
    private void dataEnergistics$teleport(ServerLevel level, double x, double y, double z, float yaw, float pitch, CallbackInfo ci) {
        if (PlatformMotion.blocked((Entity) (Object) this)) ci.cancel();
    }

    @Inject(method = "teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDLjava/util/Set;FF)Z", at = @At("HEAD"), cancellable = true)
    private void dataEnergistics$relativeTeleport(ServerLevel level, double x, double y, double z, Set<RelativeMovement> relative, float yaw, float pitch, CallbackInfoReturnable<Boolean> cir) {
        if (PlatformMotion.blocked((Entity) (Object) this)) cir.setReturnValue(false);
    }
}
