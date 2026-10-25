package com.fish_dan_.data_energistics.mixin.library.sodium;

import com.fish_dan_.data_energistics.integration.library.sodium.domain.SodiumDomainBatches;

import net.caffeinemc.mods.sodium.client.gl.device.DrawCommandList;
import net.caffeinemc.mods.sodium.client.gl.device.MultiDrawBatch;
import net.caffeinemc.mods.sodium.client.gl.tessellation.GlIndexType;
import net.caffeinemc.mods.sodium.client.render.chunk.DefaultChunkRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.LocalSectionIndex;
import net.caffeinemc.mods.sodium.client.render.chunk.data.SectionRenderDataStorage;
import net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegion;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = DefaultChunkRenderer.class, remap = false)
abstract class DomainTerrainBatchMixin {

    @WrapOperation(method = "fillCommandBuffer", at = @At(value = "INVOKE", target = "Lnet/caffeinemc/mods/sodium/client/gl/device/MultiDrawBatch;clear()V"))
    private static void dataEnergistics$begin(MultiDrawBatch batch, Operation<Void> original) {
        SodiumDomainBatches.begin(batch);
        original.call(batch);
    }

    @WrapOperation(method = "fillCommandBuffer", at = @At(value = "INVOKE", target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/data/SectionRenderDataStorage;getDataPointer(I)J"))
    private static long dataEnergistics$section(SectionRenderDataStorage storage, int section, Operation<Long> original,
                                                @Local(argsOnly = true) MultiDrawBatch batch, @Local(argsOnly = true) RenderRegion region) {
        SodiumDomainBatches.section(batch, region.getChunkX() + LocalSectionIndex.unpackX(section), region.getChunkZ() + LocalSectionIndex.unpackZ(section));
        return original.call(storage, section);
    }

    @WrapOperation(method = "executeDrawBatch", at = @At(value = "INVOKE", target = "Lnet/caffeinemc/mods/sodium/client/gl/device/DrawCommandList;multiDrawElementsBaseVertex(Lnet/caffeinemc/mods/sodium/client/gl/device/MultiDrawBatch;Lnet/caffeinemc/mods/sodium/client/gl/tessellation/GlIndexType;)V"))
    private static void dataEnergistics$draw(DrawCommandList command, MultiDrawBatch batch, GlIndexType type, Operation<Void> original) {
        SodiumDomainBatches.draw(batch, part -> original.call(command, part, type));
    }

    @WrapOperation(method = "delete", at = @At(value = "INVOKE", target = "Lnet/caffeinemc/mods/sodium/client/gl/device/MultiDrawBatch;delete()V"))
    private void dataEnergistics$delete(MultiDrawBatch batch, Operation<Void> original) {
        SodiumDomainBatches.delete(batch);
        original.call(batch);
    }
}
