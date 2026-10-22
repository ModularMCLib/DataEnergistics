package com.fish_dan_.data_energistics.common.combat.erasure.player;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.combat.erasure.lifecycle.ErasureAttachments;
import com.fish_dan_.data_energistics.common.combat.erasure.lifecycle.ErasureContext;
import com.fish_dan_.data_energistics.common.combat.erasure.lifecycle.ErasureState;
import com.fish_dan_.data_energistics.common.combat.erasure.lifecycle.ErasureState.Phase;
import com.fish_dan_.data_energistics.common.combat.erasure.strike.ErasureOutcome;
import com.fish_dan_.data_energistics.common.combat.erasure.strike.ErasureStrike;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;

import org.jspecify.annotations.Nullable;

import java.util.UUID;

/** Terminates a current player life without damage, while preserving the actual death and respawn settlement. */
public final class PlayerErasureExecutor {

    private PlayerErasureExecutor() {}

    public static ErasureOutcome execute(ServerPlayer player, ErasureStrike strike, UUID execution) {
        if (ErasureAttachments.blocksRecovery(player)) {
            return ErasureOutcome.ALREADY_HANDLED;
        }
        float previousHealth = player.getHealth();
        float previousAbsorption = player.getAbsorptionAmount();
        boolean absorptionCleared = false;
        boolean healthCleared = false;
        boolean completed = false;
        ErasureState state = null;
        try (ErasureContext context = new ErasureContext(player, strike.strikeId(), execution, strike.initiatorId())) {
            state = context.state();
            ServerPlayer initiator = strike.initiatorId() == null ? null : player.server.getPlayerList().getPlayer(strike.initiatorId());
            DamageSource cause = initiator == null ? player.damageSources().genericKill() : new DamageSource(player.damageSources().genericKill().typeHolder(), initiator);
            if (initiator != null) {
                player.setLastHurtByPlayer(initiator);
                player.setLastHurtByMob(initiator);
            }
            player.setAbsorptionAmount(0);
            absorptionCleared = true;
            player.setHealth(0);
            healthCleared = player.getHealth() == 0;
            if (!healthCleared) {
                throw new IllegalStateException("The terminal health write did not commit");
            }
            player.die(cause);
            if (state.phase() != Phase.DEATH_COMPLETED || player.getHealth() != 0) {
                throw new IllegalStateException("The player death settlement did not complete");
            }
            completed = true;
            return ErasureOutcome.PLAYER_DEATH_COMPLETED;
        } catch (RuntimeException | LinkageError failure) {
            boolean committed = state != null && (state.phase() == Phase.DEATH_COMMITTED || state.phase() == Phase.DEATH_COMPLETED);
            releaseFailedLife(player, state);
            if (!committed) {
                // Restore only our confirmed writes, and only before any death side effect began.
                if (healthCleared && player.getHealth() == 0) {
                    player.setHealth(previousHealth);
                }
                if (absorptionCleared && player.getAbsorptionAmount() == 0) {
                    player.setAbsorptionAmount(previousAbsorption);
                }
            }
            Data_Energistics.LOGGER.error("Player erasure {} for strike {} and player {} at {} ended {}",
                    execution, strike.strikeId(), player.getUUID(), player.blockPosition(),
                    committed ? "after death side effects began; no replay or inventory rollback was attempted" : "before death commitment; the life lock was released",
                    failure);
            return committed ? ErasureOutcome.PARTIAL_FAILURE : ErasureOutcome.FAILED_BEFORE_COMMIT;
        } finally {
            // Even an error propagated beyond this boundary must not leave equipment recovery indefinitely vetoed.
            if (!completed) {
                releaseFailedLife(player, state);
            }
        }
    }

    private static void releaseFailedLife(ServerPlayer player, @Nullable ErasureState state) {
        if (state != null) {
            state.fail();
            if (ErasureAttachments.find(player) == state) {
                ErasureAttachments.clear(player);
            }
        }
    }
}
