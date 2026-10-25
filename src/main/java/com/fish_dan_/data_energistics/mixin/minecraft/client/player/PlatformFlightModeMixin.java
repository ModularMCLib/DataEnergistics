package com.fish_dan_.data_energistics.mixin.minecraft.client.player;

import com.fish_dan_.data_energistics.common.data.DEAttachments;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Reuses both native always-flying checks; ordinary flight retains the original double-jump behavior. */
@Mixin(LocalPlayer.class)
abstract class PlatformFlightModeMixin {

    @WrapOperation(method = "aiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;isAlwaysFlying()Z"), require = 2)
    private boolean dataEnergistics$permanentFlight(MultiPlayerGameMode mode, Operation<Boolean> original) {
        LocalPlayer player = (LocalPlayer) (Object) this;
        return original.call(mode) || player.hasData(DEAttachments.PLATFORM_MOTION) && player.getData(DEAttachments.PLATFORM_MOTION).permanentFlight;
    }
}
