package com.fish_dan_.data_energistics.mixin.minecraft.client.render.platform;

import com.fish_dan_.data_energistics.client.weapon.platform.PlatformClientState;
import com.fish_dan_.data_energistics.common.weapon.platform.model.PlatformAbility;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LightTexture.class)
abstract class PlatformNightVisionLightMixin {

    @WrapOperation(method = "updateLightTexture", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;hasEffect(Lnet/minecraft/core/Holder;)Z"))
    private boolean dataEnergistics$platformVision(LocalPlayer player, Holder<MobEffect> effect, Operation<Boolean> original) {
        return effect == MobEffects.NIGHT_VISION && (PlatformClientState.snapshot.systems().active() & PlatformAbility.NIGHT_VISION.flag) != 0 || original.call(player, effect);
    }
}
