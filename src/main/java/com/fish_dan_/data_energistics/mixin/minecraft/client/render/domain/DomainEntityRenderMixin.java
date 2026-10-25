package com.fish_dan_.data_energistics.mixin.minecraft.client.render.domain;

import com.fish_dan_.data_energistics.client.render.domain.DomainRenderCache;
import com.fish_dan_.data_energistics.common.weapon.platform.domain.DomainStasis;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EntityRenderDispatcher.class)
abstract class DomainEntityRenderMixin {

    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;render(Lnet/minecraft/world/entity/Entity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"))
    private <E extends Entity> void dataEnergistics$frame(EntityRenderer<E> renderer, E entity, float yaw, float partial, PoseStack poses, MultiBufferSource buffers, int light, Operation<Void> original) {
        if (DomainStasis.frozen(entity)) DomainRenderCache.entity(entity, poses, captured -> original.call(renderer, entity, yaw, partial, poses, captured, light));
        else original.call(renderer, entity, yaw, partial, poses, buffers, light);
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;renderFlame(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/entity/Entity;Lorg/joml/Quaternionf;)V"))
    private void dataEnergistics$flame(EntityRenderDispatcher dispatcher, PoseStack poses, MultiBufferSource buffers, Entity entity, Quaternionf rotation, Operation<Void> original) {
        if (DomainStasis.frozen(entity)) DomainRenderCache.flame(entity, poses, captured -> original.call(dispatcher, poses, captured, entity, rotation));
        else original.call(dispatcher, poses, buffers, entity, rotation);
    }
}
