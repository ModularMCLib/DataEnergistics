package com.fish_dan_.data_energistics.mixin.library.sodium;

import com.fish_dan_.data_energistics.integration.library.sodium.domain.SodiumDomainBatches;

import net.caffeinemc.mods.sodium.client.gl.device.MultiDrawBatch;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Batch ownership moved from the renderer to cached regions in Sodium 0.8. */
@Mixin(value = MultiDrawBatch.class, remap = false)
abstract class DomainMultiDrawBatchMixin {

    @Inject(method = "clear", at = @At("HEAD"))
    private void dataEnergistics$clear(CallbackInfo ci) {
        SodiumDomainBatches.begin((MultiDrawBatch) (Object) this);
    }

    @Inject(method = "delete", at = @At("HEAD"))
    private void dataEnergistics$delete(CallbackInfo ci) {
        SodiumDomainBatches.delete((MultiDrawBatch) (Object) this);
    }
}
