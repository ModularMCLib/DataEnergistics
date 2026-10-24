package com.fish_dan_.data_energistics.common.combat.erasure.lifecycle;

import com.fish_dan_.data_energistics.common.combat.erasure.strike.ErasureOutcome;
import com.fish_dan_.data_energistics.common.combat.erasure.strike.ErasureStrike;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

/** Settles a claimed non-player life, including optional encounters, under the shared recovery lock. */
public final class LivingDeathSettlement {

    private LivingDeathSettlement() {}

    /** Runs on the target's server thread after the strike claim; failed calls release their owned life lock. */
    public static ErasureOutcome complete(LivingEntity target, ErasureStrike strike, UUID execution) {
        ErasureState state = null;
        boolean completed = false;
        float previousHealth = target.getHealth();
        try (ErasureContext context = new ErasureContext(target, strike.strikeId(), execution, strike.initiatorId())) {
            state = context.state();
            Entity owner = context.initiator();
            if (owner instanceof Player player) target.setLastHurtByPlayer(player);
            if (owner instanceof LivingEntity living) target.setLastHurtByMob(living);
            DamageSource cause = owner == null ? target.damageSources().genericKill() : new DamageSource(target.damageSources().genericKill().typeHolder(), owner);
            target.setHealth(0);
            target.die(cause);
            if (state.phase() != ErasureState.Phase.DEATH_COMPLETED) throw new IllegalStateException("Living erasure did not complete its death settlement");
            completed = true;
            return ErasureOutcome.LIVING_DEATH_COMPLETED;
        } finally {
            if (!completed && state != null) {
                boolean committed = state.phase() == ErasureState.Phase.DEATH_COMMITTED || state.phase() == ErasureState.Phase.DEATH_COMPLETED;
                state.fail();
                if (ErasureAttachments.find(target) == state) ErasureAttachments.clear(target);
                if (!committed && target.getHealth() == 0) target.setHealth(previousHealth);
            }
        }
    }
}
