package com.fish_dan_.data_energistics.common.combat.damage;

import com.fish_dan_.data_energistics.common.combat.erasure.EntityErasure;
import com.fish_dan_.data_energistics.common.combat.erasure.strike.ErasureOutcome;
import com.fish_dan_.data_energistics.common.combat.erasure.strike.ErasureStrike;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.entity.PartEntity;

import it.unimi.dsi.fastutil.objects.ObjectSets;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/** Direct health loss shared by digital projectiles, ammunition effects and explosive payloads. */
public final class DirectHealthDamage {

    private DirectHealthDamage() {}

    public static DamageSource source(LivingEntity target, @Nullable Entity owner) {
        return owner instanceof Player player ? target.damageSources().playerAttack(player) : owner instanceof LivingEntity living ? target.damageSources().mobAttack(living) : target.damageSources().magic();
    }

    /** Resolves multipart collisions once, so all damage and effects belong to the owning living entity. */
    public static @Nullable LivingEntity target(Entity hit) {
        while (hit instanceof PartEntity<?> part) hit = part.getParent();
        return hit instanceof LivingEntity living ? living : null;
    }

    /**
     * Runs on the target's server thread. Finite nonnegative damage bypasses defensive hooks and invulnerability
     * frames; creative, spectator, operator and friendly-fire restrictions remain target-selection rules.
     * Returns whether health was lost or the shared terminal flow completed. Lethal hits enter that flow while
     * the target is still alive, preserving player settlement and optional boss encounter cleanup.
     */
    public static boolean hurt(LivingEntity target, DamageSource source, double amount) {
        if (!Double.isFinite(amount) || amount < 0) throw new IllegalArgumentException("Invalid cannon health damage: " + amount);
        if (amount == 0 || !target.isAlive() || !(target.level() instanceof ServerLevel level)) return false;
        if (!level.getServer().isSameThread()) throw new IllegalStateException("Cannon damage must execute on the target's server thread");
        Entity owner = source.getEntity();
        if (target instanceof Player player && (player.isCreative() || player.isSpectator() || owner instanceof Player shooter && (!level.getServer().isPvpAllowed() || !shooter.canHarmPlayer(player)))) return false;
        if (target instanceof ServerPlayer player && player.hasPermissions(2)) return false;
        float previousHealth = target.getHealth();
        float remainingHealth = (float) (previousHealth - amount);
        if (remainingHealth <= 0.0F) {
            UUID initiator = source instanceof AttributedPlayerDamageSource playerSource ? playerSource.ownerId() : owner == null ? null : owner.getUUID();
            ErasureOutcome outcome = EntityErasure.eraseHit(target,
                    new ErasureStrike(UUID.randomUUID(), initiator, ObjectSets.emptySet()));
            return outcome == ErasureOutcome.ENTITY_REMOVED || outcome == ErasureOutcome.LIVING_DEATH_COMPLETED || outcome == ErasureOutcome.PLAYER_DEATH_COMPLETED || outcome == ErasureOutcome.GUARDIAN_ENCOUNTER_COMPLETED;
        }
        target.invulnerableTime = 0;
        target.lastHurt = 0.0F;
        if (owner instanceof Player player) target.setLastHurtByPlayer(player);
        if (owner instanceof LivingEntity livingOwner) target.setLastHurtByMob(livingOwner);
        target.getEntityData().set(LivingEntity.DATA_HEALTH_ID, remainingHealth);
        target.getCombatTracker().recordDamage(source, previousHealth - remainingHealth);
        target.hurtDuration = 10;
        target.hurtTime = target.hurtDuration;
        level.broadcastEntityEvent(target, (byte) 2);
        return remainingHealth < previousHealth;
    }
}
