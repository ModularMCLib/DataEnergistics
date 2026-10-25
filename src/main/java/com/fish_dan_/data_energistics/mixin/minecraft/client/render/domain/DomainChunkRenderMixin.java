package com.fish_dan_.data_energistics.mixin.minecraft.client.render.domain;

import com.fish_dan_.data_energistics.client.render.domain.DomainRenderCache;
import com.fish_dan_.data_energistics.client.weapon.platform.DomainClientState;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.VertexBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LevelRenderer.class)
abstract class DomainChunkRenderMixin {

    @WrapOperation(method = "renderSectionLayer", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/VertexBuffer;draw()V"))
    private void dataEnergistics$texture(VertexBuffer buffer, Operation<Void> original, @Local BlockPos origin, @Local ShaderInstance shader) {
        var domain = DomainClientState.covering(Minecraft.getInstance().level, ChunkPos.asLong(origin), null);
        if (domain == null) original.call(buffer);
        else DomainRenderCache.chunk(domain, shader, () -> original.call(buffer));
    }
}
