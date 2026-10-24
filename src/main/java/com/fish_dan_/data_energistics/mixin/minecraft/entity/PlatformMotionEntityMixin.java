package com.fish_dan_.data_energistics.mixin.minecraft.entity;

import com.fish_dan_.data_energistics.common.weapon.platform.ability.PlatformMotion;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class PlatformMotionEntityMixin {

    @Inject(method = "move", at = @At("HEAD"), cancellable = true)
    private void dataEnergistics$movement(MoverType type, Vec3 movement, CallbackInfo ci) {
        Entity entity = (Entity) (Object) this;
        if (PlatformMotion.blocked(entity)) ci.cancel();
        else if (PlatformMotion.noclip(entity)) {
            if (!entity.level().getWorldBorder().isWithinBounds(entity.getBoundingBox().move(movement))) ci.cancel();
            else entity.noPhysics = true;
        }
    }

    @Inject(method = "setPosRaw", at = @At("HEAD"), cancellable = true)
    private void dataEnergistics$position(double x, double y, double z, CallbackInfo ci) {
        if (PlatformMotion.blocked((Entity) (Object) this)) ci.cancel();
    }

    @Inject(method = "setDeltaMovement(Lnet/minecraft/world/phys/Vec3;)V", at = @At("HEAD"), cancellable = true)
    private void dataEnergistics$velocity(Vec3 movement, CallbackInfo ci) {
        if (PlatformMotion.blocked((Entity) (Object) this)) ci.cancel();
    }
}
