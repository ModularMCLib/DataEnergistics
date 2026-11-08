package com.fish_dan_.data_energistics.core.mixins.normal.net.caffeinemc.mods.sodium.client.render.chunk;

import com.fish_dan_.data_energistics.integration.library.sodium.domain.SodiumDomainBatches;

import net.caffeinemc.mods.sodium.client.gl.device.MultiDrawBatch;
import net.caffeinemc.mods.sodium.client.render.chunk.DefaultChunkRenderer;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import me.fallenbreath.conditionalmixin.api.annotation.Condition;
import me.fallenbreath.conditionalmixin.api.annotation.Restriction;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Before Sodium 0.8, the renderer owns and clears its reusable draw batch. */
@Restriction(require = @Condition(value = "sodium", versionPredicates = "[0.6,0.8)"))
@Mixin(value = DefaultChunkRenderer.class, remap = false)
abstract class DefaultChunkRendererSodiumBefore08Mixin {

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
