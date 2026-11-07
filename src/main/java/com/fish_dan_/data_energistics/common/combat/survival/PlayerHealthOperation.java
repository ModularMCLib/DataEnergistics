package com.fish_dan_.data_energistics.common.combat.survival;

import com.fish_dan_.data_energistics.common.data.DEAttachments;
import com.fish_dan_.data_energistics.common.weapon.platform.shield.ShieldHealthWrites;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;

import org.jspecify.annotations.Nullable;

/** A synchronous operation owns its decision; nested independent attacks receive their own frame. */
public final class PlayerHealthOperation implements AutoCloseable {

    public final PlayerProtection protection;
    public final @Nullable DamageSource source;
    public boolean shieldChecked;
    public boolean deathChecked;
    public boolean deathPrevented;
    private final ShieldHealthWrites writes;
    private final @Nullable PlayerHealthOperation previous;

    public PlayerHealthOperation(ServerPlayer player, @Nullable DamageSource source) {
        this.writes = player.getData(DEAttachments.SHIELD_HEALTH_WRITES);
        this.previous = writes.operation;
        this.protection = PlayerProtection.capture(player);
        this.source = source;
        writes.operation = this;
    }

    @Override
    public void close() {
        writes.operation = previous;
    }
}
