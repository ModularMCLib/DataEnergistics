package com.fish_dan_.data_energistics.common.combat.erasure.lifecycle;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;

import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * One synchronous death invocation, matched by target object and execution identity. It must be closed with
 * try-with-resources; closing drops only the invocation context, not a successfully terminated old life's lock.
 */
public final class ErasureContext implements AutoCloseable {

    private final LivingEntity target;
    private final UUID strikeId;
    private final UUID executionId;
    private final @Nullable UUID initiatorId;
    private final ErasureState state;
    private final float initialHealth;

    public ErasureContext(LivingEntity target, UUID strikeId, UUID executionId, @Nullable UUID initiatorId) {
        if (!(target.level() instanceof ServerLevel level) || !level.getServer().isSameThread()) {
            throw new IllegalStateException("Living erasure must execute on its server thread");
        }
        this.target = target;
        this.strikeId = strikeId;
        this.executionId = executionId;
        this.initiatorId = initiatorId;
        this.initialHealth = target.getHealth();
        this.state = ErasureAttachments.begin(target);
        this.state.begin(this);
    }

    public static @Nullable ErasureContext current(LivingEntity target, DamageSource cause) {
        ErasureState state = ErasureAttachments.find(target);
        ErasureContext context = state == null ? null : state.context();
        return context != null && context.target == target && cause.is(DamageTypes.GENERIC_KILL) ? context : null;
    }

    public UUID strikeId() {
        return this.strikeId;
    }

    public UUID executionId() {
        return this.executionId;
    }

    public @Nullable UUID initiatorId() {
        return this.initiatorId;
    }

    /** Resolves non-player attackers in the target level and players that initiated a remote strike. */
    public @Nullable LivingEntity initiator() {
        if (initiatorId == null) return null;
        ServerLevel level = (ServerLevel) target.level();
        Entity entity = level.getEntity(initiatorId);
        return entity instanceof LivingEntity living ? living : level.getServer().getPlayerList().getPlayer(initiatorId);
    }

    public ErasureState state() {
        return this.state;
    }

    public float initialHealth() {
        return this.initialHealth;
    }

    @Override
    public void close() {
        this.state.close(this);
    }
}
