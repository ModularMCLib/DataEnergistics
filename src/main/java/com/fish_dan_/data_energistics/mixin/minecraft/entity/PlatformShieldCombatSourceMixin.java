package com.fish_dan_.data_energistics.mixin.minecraft.entity;

import com.fish_dan_.data_energistics.common.data.DEAttachments;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.CombatTracker;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Direct health damage can announce its source through the combat tracker before writing health. */
@Mixin(CombatTracker.class)
abstract class PlatformShieldCombatSourceMixin {

    @Shadow
    @Final
    private LivingEntity mob;

    @Inject(method = "recordDamage", at = @At("HEAD"))
    private void dataEnergistics$captureSource(DamageSource source, float amount, CallbackInfo ci) {
        if (mob instanceof ServerPlayer player) {
            var writes = player.getData(DEAttachments.SHIELD_HEALTH_WRITES);
            writes.source = source;
            writes.sourceAt = player.level().getGameTime();
        }
    }
}
