package com.fish_dan_.data_energistics.core.mixins.net.minecraft.client.renderer;

import com.fish_dan_.data_energistics.client.weapon.platform.vision.PlatformVisionState;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

import org.jspecify.annotations.NullMarked;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
@NullMarked
abstract class PlatformNightVisionScaleMixin {

    @Inject(method = "getNightVisionScale", at = @At("HEAD"), cancellable = true)
    private static void dataEnergistics$platformVision(LivingEntity viewer, float partialTick, CallbackInfoReturnable<Float> cir) {
        if (viewer != Minecraft.getInstance().player) return;
        float strength = PlatformVisionState.lightStrength();
        if (strength == 1 || strength > 0 && !viewer.hasEffect(MobEffects.NIGHT_VISION)) cir.setReturnValue(strength);
    }
}
