package com.fish_dan_.data_energistics.mixin.minecraft.client.render.platform;

import com.fish_dan_.data_energistics.client.weapon.platform.vision.PlatformVisionRenderer;

import net.minecraft.client.renderer.entity.LivingEntityRenderer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(LivingEntityRenderer.class)
abstract class PlatformVisionLivingEntityMixin {

    @ModifyVariable(method = "getRenderType", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private boolean dataEnergistics$sonarBody(boolean visible) {
        return visible || PlatformVisionRenderer.sonarEntities();
    }
}
