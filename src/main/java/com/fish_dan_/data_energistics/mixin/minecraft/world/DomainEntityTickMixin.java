package com.fish_dan_.data_energistics.mixin.minecraft.world;

import com.fish_dan_.data_energistics.common.weapon.platform.domain.DomainStasis;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
abstract class DomainEntityTickMixin {

    @Shadow
    protected abstract void tickPassenger(Entity mount, Entity rider);

    @Inject(method = "tickNonPassenger", at = @At("HEAD"), cancellable = true)
    private void dataEnergistics$entity(Entity entity, CallbackInfo ci) {
        if (!DomainStasis.freezeTick(entity)) return;
        for (var passenger : entity.getPassengers()) tickPassenger(entity, passenger);
        ci.cancel();
    }

    @Inject(method = "tickPassenger", at = @At("HEAD"), cancellable = true)
    private void dataEnergistics$passenger(Entity mount, Entity rider, CallbackInfo ci) {
        if (!DomainStasis.freezeTick(rider)) return;
        for (var passenger : rider.getPassengers()) tickPassenger(rider, passenger);
        ci.cancel();
    }
}
