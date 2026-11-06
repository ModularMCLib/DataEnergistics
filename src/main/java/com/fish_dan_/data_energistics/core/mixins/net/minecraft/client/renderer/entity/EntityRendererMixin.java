package com.fish_dan_.data_energistics.core.mixins.net.minecraft.client.renderer.entity;

import com.fish_dan_.data_energistics.client.weapon.platform.vision.PlatformVisionRenderer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;

import com.mojang.blaze3d.vertex.PoseStack;
import org.jspecify.annotations.NullMarked;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
@NullMarked
abstract class EntityRendererMixin {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void dataEnergistics$modelOnly(Entity entity, float yaw, float partial, PoseStack poses, MultiBufferSource buffers, int light, CallbackInfo ci) {
        if (PlatformVisionRenderer.renderingEntities()) ci.cancel();
    }
}
