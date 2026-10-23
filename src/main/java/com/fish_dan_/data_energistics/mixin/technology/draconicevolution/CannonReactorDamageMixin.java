package com.fish_dan_.data_energistics.mixin.technology.draconicevolution;

import com.fish_dan_.data_energistics.common.entity.projectile.cannon.WeaponDamage;
import com.fish_dan_.data_energistics.integration.technology.draconicevolution.weapon.DraconicReactorPayload.CannonReactorExplosion;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import com.brandon3055.draconicevolution.blocks.reactor.ProcessExplosion;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import it.unimi.dsi.fastutil.objects.Object2FloatMap;
import it.unimi.dsi.fastutil.objects.Object2FloatOpenHashMap;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/** Retains the native reactor's delayed wave timing and amount, changing only cannon-owned health damage. */
@Mixin(targets = "com.brandon3055.draconicevolution.blocks.reactor.ProcessExplosion$1", remap = false)
public abstract class CannonReactorDamageMixin {

    @Shadow
    @Final
    ProcessExplosion this$0;

    @Unique
    private @Nullable Object2FloatMap<LivingEntity> dataEnergistics$cannonImpacts;
    @Unique
    private @Nullable DamageSource dataEnergistics$cannonSource;

    @WrapMethod(method = "execute")
    private void dataEnergistics$settleCannonWave(Object[] args, Operation<Void> original) {
        if (!(this.this$0 instanceof CannonReactorExplosion cannon)) {
            original.call((Object) args);
            return;
        }
        Object2FloatMap<LivingEntity> impacts = new Object2FloatOpenHashMap<>();
        this.dataEnergistics$cannonImpacts = impacts;
        try {
            original.call((Object) args);
            DamageSource source = this.dataEnergistics$cannonSource;
            if (source != null) {
                source = cannon.source(source);
                for (var impact : impacts.object2FloatEntrySet()) WeaponDamage.hurt(impact.getKey(), source, impact.getFloatValue());
            }
        } finally {
            this.dataEnergistics$cannonImpacts = null;
            this.dataEnergistics$cannonSource = null;
        }
    }

    @WrapOperation(method = "execute", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"))
    private boolean dataEnergistics$collectCannonWave(Entity hit, DamageSource source, float amount, Operation<Boolean> original) {
        var impacts = this.dataEnergistics$cannonImpacts;
        LivingEntity target = WeaponDamage.target(hit);
        if (impacts == null || target == null) return original.call(hit, source, amount);
        this.dataEnergistics$cannonSource = source;
        impacts.put(target, Math.max(amount, impacts.getFloat(target)));
        return true;
    }
}
