package com.fish_dan_.data_energistics.common.weapon.platform.domain;

import com.fish_dan_.data_energistics.common.combat.damage.AttributedPlayerDamageSource;

import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Optional;
import java.util.UUID;

public record DeferredSource(ResourceKey<DamageType> type, Optional<UUID> direct, Optional<UUID> owner, boolean playerOwner, Optional<Vec3> position) {

    public static final Codec<DeferredSource> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceKey.codec(Registries.DAMAGE_TYPE).fieldOf("type").forGetter(DeferredSource::type),
            UUIDUtil.CODEC.optionalFieldOf("direct").forGetter(DeferredSource::direct),
            UUIDUtil.CODEC.optionalFieldOf("owner").forGetter(DeferredSource::owner),
            Codec.BOOL.fieldOf("player_owner").forGetter(DeferredSource::playerOwner),
            Vec3.CODEC.optionalFieldOf("position").forGetter(DeferredSource::position)).apply(instance, DeferredSource::new));

    public static DeferredSource capture(DamageSource source) {
        return new DeferredSource(source.typeHolder().unwrapKey().orElseThrow(), Optional.ofNullable(source.getDirectEntity()).map(entity -> entity.getUUID()),
                source instanceof AttributedPlayerDamageSource attributed ? Optional.of(attributed.ownerId()) : Optional.ofNullable(source.getEntity()).map(entity -> entity.getUUID()), source instanceof AttributedPlayerDamageSource || source.getEntity() instanceof Player, Optional.ofNullable(source.getSourcePosition()));
    }

    public DamageSource restore(ServerLevel level) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(type),
                direct.map(level::getEntity).orElse(null), owner.map(id -> {
                    var player = level.getServer().getPlayerList().getPlayer(id);
                    return player != null ? player : level.getEntity(id);
                }).orElse(null), position.orElse(null));
    }
}
