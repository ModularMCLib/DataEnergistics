package com.fish_dan_.data_energistics.mixin.minecraft.server;

import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.governor.TrinityServerTickMetrics;

import net.minecraft.server.MinecraftServer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BooleanSupplier;

@Mixin(MinecraftServer.class)
public abstract class MinecraftServerTickMixin {

    @Inject(method = "tickServer", at = @At("HEAD"))
    private void dataEnergistics$beginCompleteServerTick(BooleanSupplier hasTimeLeft, CallbackInfo ci) {
        TrinityServerTickMetrics.beginServerTick((MinecraftServer) (Object) this);
    }

    @Inject(method = "tickServer", at = @At("RETURN"))
    private void dataEnergistics$completeServerTick(BooleanSupplier hasTimeLeft, CallbackInfo ci) {
        TrinityServerTickMetrics.completeServerTick((MinecraftServer) (Object) this);
    }
}
