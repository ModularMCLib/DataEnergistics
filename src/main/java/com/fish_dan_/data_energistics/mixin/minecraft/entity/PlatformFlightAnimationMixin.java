package com.fish_dan_.data_energistics.mixin.minecraft.entity;

import com.fish_dan_.data_energistics.common.weapon.platform.ability.PlatformMotion;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Airborne platform movement must clear the last ground stride, including its interpolated speed. */
@Mixin(LivingEntity.class)
abstract class PlatformFlightAnimationMixin {

    @Inject(method = "calculateEntityAnimation", at = @At("HEAD"), cancellable = true)
    private void dataEnergistics$airbornePose(boolean includeHeight, CallbackInfo ci) {
        if ((Object) this instanceof Player player && !player.onGround() && (player.getAbilities().flying || PlatformMotion.locked(player))) {
            player.walkAnimation.setSpeed(0);
            player.walkAnimation.update(0, 1);
            ci.cancel();
        }
    }
}
