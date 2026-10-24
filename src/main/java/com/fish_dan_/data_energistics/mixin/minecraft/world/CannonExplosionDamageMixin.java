package com.fish_dan_.data_energistics.mixin.minecraft.world;

import com.fish_dan_.data_energistics.common.entity.projectile.cannon.CannonPayloadDamage;
import com.fish_dan_.data_energistics.common.entity.projectile.cannon.WeaponDamage;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import it.unimi.dsi.fastutil.objects.Object2FloatMap;
import it.unimi.dsi.fastutil.objects.Object2FloatOpenHashMap;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/** Changes entity damage only for cannon payloads; the native explosion still owns its radius and terrain work. */
@Mixin(Explosion.class)
public abstract class CannonExplosionDamageMixin {

    @Unique
    private @Nullable Object2FloatMap<LivingEntity> dataEnergistics$cannonImpacts;
    @Unique
    private @Nullable DamageSource dataEnergistics$cannonSource;

    @Shadow
    public abstract @Nullable Entity getDirectSourceEntity();

    @WrapMethod(method = "explode")
    private void dataEnergistics$settleCannonExplosion(Operation<Void> original) {
        Entity payload = getDirectSourceEntity();
        if (!CannonPayloadDamage.isMarked(payload)) {
            original.call();
            return;
        }
        Object2FloatMap<LivingEntity> impacts = new Object2FloatOpenHashMap<>();
        this.dataEnergistics$cannonImpacts = impacts;
        try {
            original.call();
            DamageSource source = this.dataEnergistics$cannonSource;
            if (source != null) {
                source = CannonPayloadDamage.source(payload, source);
                for (var impact : impacts.object2FloatEntrySet()) WeaponDamage.hurt(impact.getKey(), source, impact.getFloatValue());
            }
        } finally {
            this.dataEnergistics$cannonImpacts = null;
            this.dataEnergistics$cannonSource = null;
        }
    }

    @WrapOperation(method = "explode", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;ignoreExplosion(Lnet/minecraft/world/level/Explosion;)Z"))
    private boolean dataEnergistics$includeCannonTarget(Entity hit, Explosion explosion, Operation<Boolean> original) {
        return (this.dataEnergistics$cannonImpacts == null || WeaponDamage.target(hit) == null) && original.call(hit, explosion);
    }

    @WrapOperation(method = "explode", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/ExplosionDamageCalculator;shouldDamageEntity(Lnet/minecraft/world/level/Explosion;Lnet/minecraft/world/entity/Entity;)Z"))
    private boolean dataEnergistics$allowCannonDamage(ExplosionDamageCalculator calculator, Explosion explosion, Entity hit, Operation<Boolean> original) {
        return this.dataEnergistics$cannonImpacts != null && WeaponDamage.target(hit) != null || original.call(calculator, explosion, hit);
    }

    @WrapOperation(method = "explode", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"))
    private boolean dataEnergistics$collectCannonDamage(Entity hit, DamageSource source, float amount, Operation<Boolean> original) {
        var impacts = this.dataEnergistics$cannonImpacts;
        LivingEntity target = WeaponDamage.target(hit);
        if (impacts == null || target == null) return original.call(hit, source, amount);
        this.dataEnergistics$cannonSource = source;
        // A multipart body and its parts can all occur in the native list; one blast damages its owner once.
        impacts.put(target, Math.max(amount, impacts.getFloat(target)));
        return true;
    }
}
