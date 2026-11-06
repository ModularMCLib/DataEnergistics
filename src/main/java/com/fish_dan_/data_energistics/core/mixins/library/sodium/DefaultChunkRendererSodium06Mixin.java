package com.fish_dan_.data_energistics.core.mixins.library.sodium;

import com.fish_dan_.data_energistics.core.mixin.condition.RequireModVersion;
import com.fish_dan_.data_energistics.core.mixin.condition.RequireMods;
import com.fish_dan_.data_energistics.integration.library.sodium.domain.SodiumDomainBatches;

import net.caffeinemc.mods.sodium.client.gl.device.MultiDrawBatch;
import net.caffeinemc.mods.sodium.client.render.chunk.DefaultChunkRenderer;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Before Sodium 0.8, the renderer owns and clears its reusable draw batch. */
@RequireMods("sodium")
@RequireModVersion(modId = "sodium", min = "0.6", max = "0.8", maxInclusive = false)
@Mixin(value = DefaultChunkRenderer.class, remap = false)
abstract class DefaultChunkRendererSodium06Mixin {

    @Dynamic("Sodium < 0.8 clears the batch here; the Mixin version condition selects this class.")
    @WrapOperation(method = "fillCommandBuffer", at = @At(value = "INVOKE", target = "Lnet/caffeinemc/mods/sodium/client/gl/device/MultiDrawBatch;clear()V"))
    private static void dataEnergistics$begin(MultiDrawBatch batch, Operation<Void> original) {
        SodiumDomainBatches.begin(batch);
        original.call(batch);
    }

    @Dynamic("Sodium < 0.8 deletes the batch here; the Mixin version condition selects this class.")
    @WrapOperation(method = "delete", at = @At(value = "INVOKE", target = "Lnet/caffeinemc/mods/sodium/client/gl/device/MultiDrawBatch;delete()V"))
    private void dataEnergistics$delete(MultiDrawBatch batch, Operation<Void> original) {
        SodiumDomainBatches.delete(batch);
        original.call(batch);
    }
}
