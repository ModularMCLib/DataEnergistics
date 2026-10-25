package com.fish_dan_.data_energistics.mixin.minecraft.client.render.platform;

import com.fish_dan_.data_energistics.client.weapon.platform.PlatformClientState;
import com.fish_dan_.data_energistics.common.weapon.platform.model.PlatformAbility;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(FogRenderer.class)
abstract class PlatformNightVisionFogMixin {

    @WrapOperation(method = "setupColor", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;hasEffect(Lnet/minecraft/core/Holder;)Z"))
    private static boolean dataEnergistics$platformVision(LivingEntity entity, Holder<MobEffect> effect, Operation<Boolean> original) {
        return effect == MobEffects.NIGHT_VISION && entity == Minecraft.getInstance().player && (PlatformClientState.snapshot.systems().active() & PlatformAbility.NIGHT_VISION.flag) != 0 || original.call(entity, effect);
    }
}
