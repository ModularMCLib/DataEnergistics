package com.fish_dan_.data_energistics.core.mixins.net.minecraft.client.renderer;

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
import me.fallenbreath.conditionalmixin.api.annotation.Condition;
import me.fallenbreath.conditionalmixin.api.annotation.Restriction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Restriction(conflict = @Condition("sodium"))
@Mixin(LevelRenderer.class)
abstract class LevelRendererMixin {

    @WrapOperation(method = "renderSectionLayer", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/VertexBuffer;draw()V"))
    private void dataEnergistics$texture(VertexBuffer buffer, Operation<Void> original, @Local BlockPos origin, @Local ShaderInstance shader) {
        long chunk = ChunkPos.asLong(origin);
        var domain = DomainClientState.covering(Minecraft.getInstance().level, chunk, null);
        if (domain == null) original.call(buffer);
        else DomainRenderCache.chunk(chunk, domain, shader, () -> original.call(buffer));
    }
}
