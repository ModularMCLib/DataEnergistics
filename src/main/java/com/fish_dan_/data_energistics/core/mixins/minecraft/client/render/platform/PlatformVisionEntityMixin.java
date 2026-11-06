package com.fish_dan_.data_energistics.core.mixins.minecraft.client.render.platform;

import com.fish_dan_.data_energistics.client.weapon.platform.vision.PlatformVisionRenderer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;

import com.mojang.blaze3d.vertex.PoseStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
abstract class PlatformVisionEntityMixin {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void dataEnergistics$modelOnly(Entity entity, float yaw, float partial, PoseStack poses, MultiBufferSource buffers, int light, CallbackInfo ci) {
        if (PlatformVisionRenderer.renderingEntities()) ci.cancel();
    }
}
