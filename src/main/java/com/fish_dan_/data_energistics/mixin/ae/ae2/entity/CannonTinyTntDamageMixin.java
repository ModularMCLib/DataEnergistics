package com.fish_dan_.data_energistics.mixin.ae.ae2.entity;

import com.fish_dan_.data_energistics.common.entity.projectile.cannon.CannonPayloadDamage;
import com.fish_dan_.data_energistics.common.entity.projectile.cannon.WeaponDamage;

import appeng.entity.TinyTNTPrimedEntity;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/** AE2 applies Tiny TNT damage outside Explosion.explode; keep its native terrain and singularity behavior. */
@Mixin(TinyTNTPrimedEntity.class)
public abstract class CannonTinyTntDamageMixin {

    @Unique
    private @Nullable ObjectSet<LivingEntity> dataEnergistics$cannonTargets;

    @WrapMethod(method = "explode")
    private void dataEnergistics$trackCannonExplosion(Operation<Void> original) {
        if (!CannonPayloadDamage.isMarked((Entity) (Object) this)) {
            original.call();
            return;
        }
        this.dataEnergistics$cannonTargets = new ObjectOpenHashSet<>();
        try {
            original.call();
        } finally {
            this.dataEnergistics$cannonTargets = null;
        }
    }

    @WrapOperation(method = "explode", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"))
    private boolean dataEnergistics$damageCannonTarget(Entity hit, DamageSource source, float amount, Operation<Boolean> original) {
        var targets = this.dataEnergistics$cannonTargets;
        LivingEntity target = WeaponDamage.target(hit);
        if (targets == null || target == null) return original.call(hit, source, amount);
        // AE2 applies the same six points to every entry; a multipart owner receives that amount once.
        return targets.add(target) && WeaponDamage.hurt(target, CannonPayloadDamage.source((Entity) (Object) this, source), amount);
    }
}
