package com.fish_dan_.data_energistics.common.combat.damage;

import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;

import lombok.Getter;
import lombok.experimental.Accessors;

import java.util.UUID;

/** Keeps a persisted player's identity when the current server has no live attacker entity. */
@Getter
@Accessors(fluent = true)
public final class AttributedPlayerDamageSource extends DamageSource {

    private final UUID ownerId;

    public AttributedPlayerDamageSource(ServerLevel level, UUID ownerId) {
        super(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(DamageTypes.PLAYER_ATTACK),
                level.getServer().getPlayerList().getPlayer(ownerId));
        this.ownerId = ownerId;
    }
}
