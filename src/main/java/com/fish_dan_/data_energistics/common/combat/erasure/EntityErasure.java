package com.fish_dan_.data_energistics.common.combat.erasure;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.combat.erasure.lifecycle.LivingDeathSettlement;
import com.fish_dan_.data_energistics.common.combat.erasure.player.PlayerErasureExecutor;
import com.fish_dan_.data_energistics.common.combat.erasure.strike.ErasureOutcome;
import com.fish_dan_.data_energistics.common.combat.erasure.strike.ErasureStrike;
import com.fish_dan_.data_energistics.integration.MOD;
import com.fish_dan_.data_energistics.integration.technology.draconicevolution.orbital.DraconicGuardianErasureAdapter;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.entity.PartEntity;

import java.util.UUID;

/** Shared server-thread termination after a weapon has checked its own targeting and hit-volume rules. */
public final class EntityErasure {

    private EntityErasure() {}

    /**
     * Erases a hit entity without entering the damage pipeline. Multipart hits resolve to their owning entity;
     * repeated hits on an already erased owner do nothing. Frozen authorization exemptions and privileged players
     * remain protected. Living entities settle their death, loot and experience exactly once before removal.
     * Both arguments must be non-null, and the caller must run on the hit entity's server thread.
     */
    public static ErasureOutcome eraseHit(Entity hit, ErasureStrike strike) {
        if (!(hit.level() instanceof ServerLevel level) || !level.getServer().isSameThread()) {
            throw new IllegalStateException("Entity erasure must execute on the target's server thread");
        }
        Entity target = hit;
        while (target instanceof PartEntity<?> part) {
            target = part.getParent();
        }
        if (strike.exempts(target.getUUID())) {
            return ErasureOutcome.EXEMPT;
        }
        if (target instanceof Player player && (player.isCreative() || player.isSpectator())) {
            return ErasureOutcome.EXEMPT;
        }
        if (target instanceof ServerPlayer player && player.hasPermissions(2)) {
            return ErasureOutcome.EXEMPT;
        }
        if (target.isRemoved() || !target.isAlive()) {
            return ErasureOutcome.INACTIVE;
        }
        UUID execution = strike.begin(target.getUUID());
        if (execution == null) {
            return ErasureOutcome.ALREADY_HANDLED;
        }
        ErasureOutcome outcome;
        try {
            if (target instanceof ServerPlayer player) {
                outcome = PlayerErasureExecutor.execute(player, strike, execution);
            } else if (MOD.DRACONIC_EVOLUTION.isLoaded() && DraconicGuardianErasureAdapter.supports(target)) {
                outcome = DraconicGuardianErasureAdapter.execute(target, strike, execution);
            } else if (target instanceof LivingEntity living) {
                outcome = LivingDeathSettlement.complete(living, strike, execution);
            } else {
                target.setRemoved(RemovalReason.DISCARDED);
                outcome = target.isRemoved() ? ErasureOutcome.ENTITY_REMOVED : ErasureOutcome.PARTIAL_FAILURE;
            }
        } catch (RuntimeException | LinkageError failure) {
            Data_Energistics.LOGGER.error("Entity erasure {} for strike {} and subject {} at {} failed; the attempt will not replay",
                    execution, strike.strikeId(), target.getUUID(), target.blockPosition(), failure);
            outcome = ErasureOutcome.PARTIAL_FAILURE;
        }
        strike.complete(target.getUUID(), execution, outcome);
        return outcome;
    }
}
