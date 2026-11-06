package com.fish_dan_.data_energistics.core.mixins.net.minecraft.client.renderer.entity;

import com.fish_dan_.data_energistics.client.weapon.platform.vision.PlatformVisionRenderer;

import net.minecraft.client.renderer.entity.LivingEntityRenderer;

import org.jspecify.annotations.NullMarked;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(LivingEntityRenderer.class)
@NullMarked
abstract class LivingEntityRendererMixin {

    @ModifyVariable(method = "getRenderType", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private boolean dataEnergistics$sonarBody(boolean visible) {
        return visible || PlatformVisionRenderer.sonarEntities();
    }
}
