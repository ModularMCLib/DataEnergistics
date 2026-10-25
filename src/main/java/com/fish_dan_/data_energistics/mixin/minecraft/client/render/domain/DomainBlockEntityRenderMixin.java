package com.fish_dan_.data_energistics.mixin.minecraft.client.render.domain;

import com.fish_dan_.data_energistics.client.render.domain.DomainRenderCache;
import com.fish_dan_.data_energistics.common.weapon.platform.domain.DomainStasis;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.world.level.block.entity.BlockEntity;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(BlockEntityRenderDispatcher.class)
abstract class DomainBlockEntityRenderMixin {

    @WrapOperation(method = "setupAndRender", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/blockentity/BlockEntityRenderer;render(Lnet/minecraft/world/level/block/entity/BlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V"))
    private static <E extends BlockEntity> void dataEnergistics$frame(BlockEntityRenderer<E> renderer, E entity, float partial, PoseStack poses, MultiBufferSource buffers, int light, int overlay, Operation<Void> original) {
        if (entity.getLevel() != null && DomainStasis.frozen(entity.getLevel(), entity.getBlockPos())) DomainRenderCache.blockEntity(entity, poses, captured -> original.call(renderer, entity, partial, poses, captured, light, overlay));
        else original.call(renderer, entity, partial, poses, buffers, light, overlay);
    }
}
