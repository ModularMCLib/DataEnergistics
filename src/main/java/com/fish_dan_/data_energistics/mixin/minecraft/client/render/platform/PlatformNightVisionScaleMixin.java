package com.fish_dan_.data_energistics.mixin.minecraft.client.render.platform;

import com.fish_dan_.data_energistics.client.weapon.platform.PlatformClientState;
import com.fish_dan_.data_energistics.common.weapon.platform.model.PlatformAbility;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.LivingEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
abstract class PlatformNightVisionScaleMixin {

    @Inject(method = "getNightVisionScale", at = @At("HEAD"), cancellable = true)
    private static void dataEnergistics$platformVision(LivingEntity viewer, float partialTick, CallbackInfoReturnable<Float> cir) {
        if (viewer == Minecraft.getInstance().player && (PlatformClientState.snapshot.systems().active() & PlatformAbility.NIGHT_VISION.flag) != 0) cir.setReturnValue(1f);
    }
}
