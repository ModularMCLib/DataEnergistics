package com.fish_dan_.data_energistics.mixin.minecraft.entity;

import com.fish_dan_.data_energistics.common.weapon.platform.domain.DomainStasis;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
abstract class DomainPlayerTickMixin {

    @Inject(method = { "tick", "doTick" }, at = @At("HEAD"), cancellable = true)
    private void dataEnergistics$freeze(CallbackInfo ci) {
        if (DomainStasis.frozen((Entity) (Object) this)) ci.cancel();
    }
}
