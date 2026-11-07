package com.fish_dan_.data_energistics.common.combat.survival;

import com.fish_dan_.data_energistics.common.combat.erasure.lifecycle.ErasureAttachments;
import com.fish_dan_.data_energistics.common.data.DEAttachments;
import com.fish_dan_.data_energistics.common.effect.reboot.RadixReboot;
import com.fish_dan_.data_energistics.common.weapon.platform.shield.PlatformShield;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;

import it.unimi.dsi.fastutil.floats.FloatConsumer;
import org.jspecify.annotations.Nullable;

import java.util.function.BooleanSupplier;

/** All native health and death entrances share one decision before any death side effects. Server thread only. */
public final class PlayerSurvival {

    private PlayerSurvival() {}

    /** Equipment-dependent protection starts after the complete player constructor has returned. */
    public static void initialize(ServerPlayer player) {
        player.getData(DEAttachments.SHIELD_HEALTH_WRITES).initialized = true;
        observe(player);
    }

    /** Begins an independent attack; only nested entrances belonging to this operation share its decision. */
    public static PlayerHealthOperation damage(ServerPlayer player, @Nullable DamageSource source) {
        return new PlayerHealthOperation(player, source);
    }

    public static boolean hurt(Entity entity, DamageSource source, BooleanSupplier damage) {
        if (!(entity instanceof ServerPlayer player) || ErasureAttachments.blocksRecovery(player)) return damage.getAsBoolean();
        try (var ignored = damage(player, source)) {
            return damage.getAsBoolean();
        }
    }

    public static double absorb(ServerPlayer player, double amount, @Nullable DamageSource source) {
        var writes = player.getData(DEAttachments.SHIELD_HEALTH_WRITES);
        if (writes.operation == null) {
            try (var ignored = new PlayerHealthOperation(player, source)) {
                return absorb(player, amount, source);
            }
        }
        writes.operation.shieldChecked = true;
        double remaining = PlatformShield.absorb(player, writes.operation.protection, amount, source);
        if (amount > 0 && remaining == 0) writes.operation.deathPrevented = true;
        return remaining;
    }

    /** A settled damage write skips shield payment, while still receiving the common lethal-loss decision. */
    public static void writeHealth(ServerPlayer player, float requested, boolean damageSettled, FloatConsumer write) {
        var writes = player.getData(DEAttachments.SHIELD_HEALTH_WRITES);
        if (writes.settled != 0 || ErasureAttachments.blocksRecovery(player)) {
            write.accept(requested);
            return;
        }
        if (writes.operation == null) {
            try (var ignored = new PlayerHealthOperation(player, recentSource(player))) {
                writeHealth(player, requested, damageSettled, write);
            }
            return;
        }
        boolean lethalMaximum = player.getMaxHealth() < 1;
        if (lethalMaximum && writes.maximumHealth != null && prevent(player, writes.operation)) return;
        float current = player.getHealth();
        // These APIs are third-party input boundaries too; NaN must not poison the authoritative health field.
        float target = Float.isNaN(requested) ? 0 : Math.max(0, Math.min(requested, player.getMaxHealth()));
        double remaining = (double) current - target;
        if (remaining > 0 && !damageSettled && !lethalMaximum) remaining = absorb(player, remaining, writes.operation.source);
        float adjusted = (float) (current - remaining);
        if (target <= 0 && adjusted > 0) {
            writes.operation.deathPrevented = true;
        } else if (adjusted <= 0 && prevent(player, writes.operation)) {
            return;
        }
        writes.settled++;
        try {
            write.accept(adjusted);
        } finally {
            writes.settled--;
        }
        if (player.getHealth() > 0) writes.lastHealth = player.getHealth();
    }

    /** Called before die/kill/removal; it never revives a life already claimed by orbital erasure. */
    public static boolean preventDeath(ServerPlayer player, @Nullable DamageSource source) {
        if (ErasureAttachments.blocksRecovery(player) || player.isRemoved()) return false;
        var writes = player.getData(DEAttachments.SHIELD_HEALTH_WRITES);
        if (writes.settled != 0 || writes.deathCommitted) return false;
        if (writes.operation != null) return prevent(player, writes.operation);
        try (var operation = new PlayerHealthOperation(player, source)) {
            return prevent(player, operation);
        }
    }

    public static void die(ServerPlayer player, DamageSource source, Runnable death) {
        var writes = player.getData(DEAttachments.SHIELD_HEALTH_WRITES);
        if (writes.operation == null) {
            try (var ignored = new PlayerHealthOperation(player, source)) {
                die(player, source, death);
            }
            return;
        }
        if (preventDeath(player, source)) return;
        death.run();
    }

    public static boolean preventRemoval(Entity entity, RemovalReason reason) {
        if (!(entity instanceof ServerPlayer player) || reason != RemovalReason.KILLED && reason != RemovalReason.DISCARDED || player.isRemoved()) return false;
        if (player.getData(DEAttachments.SHIELD_HEALTH_WRITES).deathCommitted) return false;
        return preventDeath(player, recentSource(player));
    }

    private static boolean prevent(ServerPlayer player, PlayerHealthOperation operation) {
        if (operation.deathChecked || operation.deathPrevented) return operation.deathPrevented;
        operation.deathChecked = true;
        if (!operation.shieldChecked) {
            operation.shieldChecked = true;
            operation.deathPrevented = PlatformShield.resistDeath(player, operation.protection, operation.source);
        }
        if (!operation.deathPrevented) operation.deathPrevented = RadixReboot.tryPreventDeath(player, operation.protection.reboot());
        if (!operation.deathPrevented) return false;
        restoreMaximum(player);
        var writes = player.getData(DEAttachments.SHIELD_HEALTH_WRITES);
        if (player.getHealth() <= 0) {
            writes.settled++;
            try {
                player.setHealth(Math.clamp(writes.lastHealth, 1, player.getMaxHealth()));
            } finally {
                writes.settled--;
            }
        }
        ((LivingDeathState) player).dataEnergistics$cancelPendingDeath();
        player.deathTime = 0;
        return true;
    }

    /** The typed boundary keeps Mixin's synthetic inheritance out of ordinary instanceof analysis. */
    public static void maximumChanged(Entity entity, Holder<Attribute> attribute, Runnable update) {
        if (attribute == Attributes.MAX_HEALTH && entity instanceof ServerPlayer player) maximumChanged(player, update);
        else update.run();
    }

    /** Captures valid attribute updates; only a defended lethal update is rolled back. */
    private static void maximumChanged(ServerPlayer player, Runnable update) {
        var writes = player.getData(DEAttachments.SHIELD_HEALTH_WRITES);
        if (ErasureAttachments.blocksRecovery(player) || writes.settled != 0) {
            update.run();
            return;
        }
        try (var operation = new PlayerHealthOperation(player, recentSource(player))) {
            if (player.getMaxHealth() < 1 && writes.maximumHealth != null) prevent(player, operation);
            update.run();
            observe(player);
        }
    }

    public static void restoreMaximum(ServerPlayer player) {
        var writes = player.getData(DEAttachments.SHIELD_HEALTH_WRITES);
        if (player.getMaxHealth() < 1 && writes.maximumHealth != null) {
            writes.maximumHealth.restore();
        }
    }

    public static void observe(ServerPlayer player) {
        if (!player.isAlive()) return;
        var writes = player.getData(DEAttachments.SHIELD_HEALTH_WRITES);
        writes.lastHealth = player.getHealth();
        if (player.getMaxHealth() >= 1) writes.maximumHealth = new MaximumHealthState(player.getAttribute(Attributes.MAX_HEALTH));
    }

    public static @Nullable DamageSource recentSource(ServerPlayer player) {
        var writes = player.getData(DEAttachments.SHIELD_HEALTH_WRITES);
        return writes.sourceAt == player.level().getGameTime() ? writes.source : null;
    }
}
