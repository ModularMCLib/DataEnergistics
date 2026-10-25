package com.fish_dan_.data_energistics.mixin.minecraft.entity;

import com.fish_dan_.data_energistics.common.data.DEAttachments;
import com.fish_dan_.data_energistics.common.weapon.platform.ability.PlatformMotion;
import com.fish_dan_.data_energistics.common.weapon.platform.domain.DeferredImpact;
import com.fish_dan_.data_energistics.common.weapon.platform.domain.DomainStasis;
import com.fish_dan_.data_energistics.common.weapon.platform.domain.StasisDomainSavedData;

import net.minecraft.server.level.ServerLevel;
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
        Entity entity = (Entity) (Object) this;
        boolean frozen = DomainStasis.frozen(entity);
        if (frozen && entity.level() instanceof ServerLevel level) {
            Vec3 change = movement.subtract(entity.getDeltaMovement());
            if (change.lengthSqr() > 0) StasisDomainSavedData.get(level.getServer()).defer(entity, new DeferredImpact.Push(change));
        }
        if (frozen || PlatformMotion.locked(entity) && entity.getData(DEAttachments.PLATFORM_MOTION).permits == 0) ci.cancel();
    }
}
