package com.fish_dan_.data_energistics.mixin.minecraft.world;

import com.fish_dan_.data_energistics.common.weapon.platform.domain.StasisDomainSavedData;

import net.minecraft.world.ticks.LevelTicks;
import net.minecraft.world.ticks.ScheduledTick;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelTicks.class)
abstract class DomainScheduledTickMixin<T> {

    @Inject(method = "schedule(Lnet/minecraft/world/ticks/ScheduledTick;)V", at = @At("HEAD"), cancellable = true)
    @SuppressWarnings("unchecked") // The target LevelTicks<T> and this injected argument share the same type parameter.
    private void dataEnergistics$pause(ScheduledTick<T> tick, CallbackInfo ci) {
        if (StasisDomainSavedData.pause((LevelTicks<T>) (Object) this, tick)) ci.cancel();
    }
}
