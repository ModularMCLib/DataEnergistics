package com.fish_dan_.data_energistics.core.mixins.normal.net.minecraft.client.renderer;

import com.fish_dan_.data_energistics.client.weapon.platform.vision.PlatformVisionState;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.jspecify.annotations.NullMarked;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LightTexture.class)
@NullMarked
abstract class LightTextureMixin {

    @WrapOperation(method = "updateLightTexture", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;hasEffect(Lnet/minecraft/core/Holder;)Z"))
    private boolean dataEnergistics$platformVision(LocalPlayer player, Holder<MobEffect> effect, Operation<Boolean> original) {
        return effect == MobEffects.NIGHT_VISION && PlatformVisionState.lightStrength() > 0 || original.call(player, effect);
    }
}
