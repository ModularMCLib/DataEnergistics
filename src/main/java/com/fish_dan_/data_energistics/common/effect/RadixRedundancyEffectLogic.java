package com.fish_dan_.data_energistics.common.effect;

import com.fish_dan_.data_energistics.common.data.DEMobEffects;
import com.fish_dan_.data_energistics.common.entity.projectile.cannon.WeaponDamage;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import org.jspecify.annotations.Nullable;

public final class RadixRedundancyEffectLogic {

    private static final int MAX_AMPLIFIER = 9;
    private static final float WEAPON_DAMAGE_BONUS_PER_LEVEL = 0.05F;
    private static final float MISSING_HEALTH_DAMAGE_RATIO = 0.25F;

    private RadixRedundancyEffectLogic() {}

    public static int layers(LivingEntity target) {
        MobEffectInstance effect = target.getEffect(DEMobEffects.RADIX_REDUNDANCY);
        return effect == null ? 0 : Math.min(32, effect.getAmplifier() + 1);
    }

    public static void applyOrBurst(LivingEntity target, int durationTicks, @Nullable Entity attacker, float weaponDamage) {
        apply(target, durationTicks, attacker, weaponDamage, false, 1, false);
    }

    /** Cannon hits bypass defenses for both layer bonuses and bursts; sword applications keep their own rules. */
    public static void applyCannonHit(LivingEntity target, int durationTicks, @Nullable Entity attacker, float weaponDamage,
                                      boolean railMaximumHealthDamage, float charge) {
        apply(target, durationTicks, attacker, weaponDamage, railMaximumHealthDamage, charge, true);
    }

    private static void apply(LivingEntity target, int durationTicks, @Nullable Entity attacker, float weaponDamage,
                              boolean railMaximumHealthDamage, float charge, boolean directDamage) {
        MobEffectInstance existingEffect = target.getEffect(DEMobEffects.RADIX_REDUNDANCY);
        if (existingEffect == null) {
            target.addEffect(new MobEffectInstance(DEMobEffects.RADIX_REDUNDANCY, durationTicks, 0, false, true, true));
            return;
        }

        if (railMaximumHealthDamage) {
            dealDamage(target, attacker, target.getMaxHealth() * 0.15F * (existingEffect.getAmplifier() + 1) * charge, directDamage);
        } else {
            float damage = Math.max(0.0F, weaponDamage) * WEAPON_DAMAGE_BONUS_PER_LEVEL * (existingEffect.getAmplifier() + 1);
            dealDamage(target, attacker, damage, directDamage);
        }
        if (!target.isAlive()) {
            return;
        }

        int nextAmplifier = existingEffect.getAmplifier() + 1;
        if (nextAmplifier >= MAX_AMPLIFIER) {
            target.removeEffect(DEMobEffects.RADIX_REDUNDANCY);
            float missingHealth = Math.max(0.0F, target.getMaxHealth() - target.getHealth());
            dealDamage(target, attacker, missingHealth * MISSING_HEALTH_DAMAGE_RATIO, directDamage);
            return;
        }

        int duration = Math.max(existingEffect.getDuration(), durationTicks);
        target.addEffect(new MobEffectInstance(DEMobEffects.RADIX_REDUNDANCY, duration, nextAmplifier, false, true, true));
    }

    private static void dealDamage(LivingEntity target, @Nullable Entity attacker, float damage, boolean directDamage) {
        if (damage <= 0.0F) {
            return;
        }

        if (directDamage) WeaponDamage.hurt(target, createDamageSource(target, attacker), damage);
        else target.hurt(createDamageSource(target, attacker), damage);
    }

    private static DamageSource createDamageSource(LivingEntity target, @Nullable Entity attacker) {
        return attacker instanceof Player player ? target.damageSources().playerAttack(player) : attacker instanceof LivingEntity livingAttacker ? target.damageSources().mobAttack(livingAttacker) : target.damageSources().magic();
    }
}
