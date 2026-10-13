package com.fish_dan_.data_energistics.effect;

import com.fish_dan_.data_energistics.entity.projectile.cannon.WeaponDamage;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import org.jspecify.annotations.Nullable;

/** Persisted weapon burn; refreshing its duration never resets its damage cadence. */
public final class WeaponBurn {

    private static final String KEY = "DataEnergisticsWeaponBurn";
    private static final String VANILLA_RECOVERY_KEY = "VanillaRecovery";
    private static final String DECAY_TO_ZERO_KEY = "DecayToZero";
    private static final String DECAY_DURATION_KEY = "DecayDuration";
    private static final int VANILLA_RECOVERY_TICKS = 20;

    public static void apply(LivingEntity target, @Nullable Entity owner, int duration, float damage) {
        apply(target, owner, duration, damage, false, false);
    }

    /** Applies a burn that hands damage back to vanilla fire after the custom phase ends. */
    public static void applyWithVanillaRecovery(LivingEntity target, @Nullable Entity owner, int duration, float damage) {
        apply(target, owner, duration, damage, true, false);
    }

    /** Applies a burn whose per-second damage decreases linearly to zero over its duration. */
    public static void applyWithDecay(LivingEntity target, @Nullable Entity owner, int duration, float damage) {
        apply(target, owner, duration, damage, false, true);
    }

    private static void apply(LivingEntity target, @Nullable Entity owner, int duration, float damage,
                              boolean vanillaRecovery, boolean decayToZero) {
        if (target.level().isClientSide || target.fireImmune() || target.hasEffect(MobEffects.FIRE_RESISTANCE) || target.isInWaterRainOrBubble()) return;
        CompoundTag data = target.getPersistentData();
        CompoundTag burn = data.getCompound(KEY);
        burn.putInt("Remaining", Math.max(duration, burn.getInt("Remaining")));
        burn.putFloat("Damage", Math.max(damage, burn.getFloat("Damage")));
        burn.putBoolean(VANILLA_RECOVERY_KEY, vanillaRecovery);
        burn.putBoolean(DECAY_TO_ZERO_KEY, decayToZero);
        if (decayToZero) burn.putInt(DECAY_DURATION_KEY, Math.max(duration, burn.getInt(DECAY_DURATION_KEY)));
        if (owner != null) burn.putUUID("Owner", owner.getUUID());
        data.put(KEY, burn);
        target.setRemainingFireTicks(Math.max(target.getRemainingFireTicks(), duration + 1));
    }

    @SubscribeEvent
    public void tick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity target) || !(target.level() instanceof ServerLevel level)) return;
        CompoundTag data = target.getPersistentData();
        if (!data.contains(KEY)) return;
        CompoundTag burn = data.getCompound(KEY);
        if (!target.isAlive() || !target.isOnFire() || target.fireImmune() || target.hasEffect(MobEffects.FIRE_RESISTANCE) || target.isInWaterRainOrBubble()) {
            data.remove(KEY);
            return;
        }
        int remaining = burn.getInt("Remaining") - 1;
        int cadence = burn.getInt("Cadence") + 1;
        if (cadence >= 20) {
            Entity owner = burn.hasUUID("Owner") ? level.getEntity(burn.getUUID("Owner")) : null;
            float damage = burn.getFloat("Damage");
            if (burn.getBoolean(DECAY_TO_ZERO_KEY)) {
                int duration = Math.max(1, burn.getInt(DECAY_DURATION_KEY));
                damage *= Math.clamp((float) remaining / duration, 0.0F, 1.0F);
            }
            WeaponDamage.hurt(target, level.damageSources().source(DamageTypes.IN_FIRE, owner), damage);
            cadence = 0;
        }
        if (remaining <= 0) {
            boolean vanillaRecovery = burn.getBoolean(VANILLA_RECOVERY_KEY);
            data.remove(KEY);
            if (vanillaRecovery) {
                target.setRemainingFireTicks(Math.max(target.getRemainingFireTicks(), VANILLA_RECOVERY_TICKS));
            } else {
                target.clearFire();
            }
        } else {
            burn.putInt("Remaining", remaining);
            burn.putInt("Cadence", cadence);
        }
    }

    /** Suppress only vanilla periodic burning while our timed burn owns that periodic damage. */
    @SubscribeEvent
    public void vanillaBurn(LivingIncomingDamageEvent event) {
        if (event.getSource().is(DamageTypes.ON_FIRE) && event.getEntity().getPersistentData().contains(KEY)) {
            event.setCanceled(true);
        }
    }
}
