package com.fish_dan_.data_energistics.common.effect;

import com.fish_dan_.data_energistics.common.data.DEAttachments;
import com.fish_dan_.data_energistics.common.entity.projectile.cannon.WeaponDamage;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import org.jspecify.annotations.Nullable;

import java.util.Optional;

/** Persisted weapon burn; refreshing its duration never resets its damage cadence. */
public final class WeaponBurn {

    private static final int AFTERBURN_TICKS = 20;

    public static void apply(LivingEntity target, @Nullable Entity owner, int duration, float damage) {
        apply(target, owner, duration, damage, false, false);
    }

    /** Applies a burn followed by one second of one-point direct afterburn damage. */
    public static void applyWithAfterburn(LivingEntity target, @Nullable Entity owner, int duration, float damage) {
        apply(target, owner, duration, damage, true, false);
    }

    /** Applies a burn whose per-second damage decreases linearly to zero over its duration. */
    public static void applyWithDecay(LivingEntity target, @Nullable Entity owner, int duration, float damage) {
        apply(target, owner, duration, damage, false, true);
    }

    private static void apply(LivingEntity target, @Nullable Entity owner, int duration, float damage,
                              boolean afterburn, boolean decayToZero) {
        if (target.level().isClientSide || !target.isAlive() || target.isInWaterRainOrBubble()) return;
        WeaponBurnState burn = target.hasData(DEAttachments.WEAPON_BURN) ? target.getData(DEAttachments.WEAPON_BURN) : WeaponBurnState.EMPTY;
        target.setData(DEAttachments.WEAPON_BURN, new WeaponBurnState(
                Math.max(duration, burn.remaining()), burn.cadence(), Math.max(damage, burn.damage()),
                afterburn, decayToZero, decayToZero ? Math.max(duration, burn.decayDuration()) : burn.decayDuration(),
                owner == null ? burn.owner() : Optional.of(owner.getUUID())));
        target.setRemainingFireTicks(Math.max(target.getRemainingFireTicks(), duration + 1));
    }

    @SubscribeEvent
    public void tick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity target) || !(target.level() instanceof ServerLevel level)) return;
        if (!target.hasData(DEAttachments.WEAPON_BURN)) return;
        WeaponBurnState burn = target.getData(DEAttachments.WEAPON_BURN);
        if (!target.isAlive() || !target.isOnFire() && !target.fireImmune() || target.isInWaterRainOrBubble()) {
            target.removeData(DEAttachments.WEAPON_BURN);
            return;
        }
        int remaining = burn.remaining() - 1;
        int cadence = burn.cadence() + 1;
        if (cadence >= 20) {
            Entity owner = burn.owner().map(level::getEntity).orElse(null);
            float damage = burn.damage();
            if (burn.decayToZero()) {
                int duration = Math.max(1, burn.decayDuration());
                damage *= Math.clamp((float) remaining / duration, 0.0F, 1.0F);
            }
            WeaponDamage.hurt(target, level.damageSources().source(DamageTypes.IN_FIRE, owner), damage);
            cadence = 0;
        }
        if (remaining <= 0) {
            if (burn.afterburn()) {
                target.setData(DEAttachments.WEAPON_BURN, new WeaponBurnState(
                        AFTERBURN_TICKS, cadence, 1.0F, false, false, burn.decayDuration(), burn.owner()));
                target.setRemainingFireTicks(Math.max(target.getRemainingFireTicks(), AFTERBURN_TICKS + 1));
            } else {
                target.removeData(DEAttachments.WEAPON_BURN);
                target.clearFire();
            }
        } else {
            target.setData(DEAttachments.WEAPON_BURN, new WeaponBurnState(
                    remaining, cadence, burn.damage(), burn.afterburn(), burn.decayToZero(), burn.decayDuration(), burn.owner()));
        }
    }

    /** Suppress only vanilla periodic burning while our timed burn owns that periodic damage. */
    @SubscribeEvent
    public void vanillaBurn(LivingIncomingDamageEvent event) {
        if (event.getSource().is(DamageTypes.ON_FIRE) && event.getEntity().hasData(DEAttachments.WEAPON_BURN)) {
            event.setCanceled(true);
        }
    }
}
