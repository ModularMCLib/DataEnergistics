package com.fish_dan_.data_energistics.common.entity.projectile.cannon;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/** Spawn-only wire snapshot; no NBT intermediary is used for shot parameters or velocity. */
public record CannonBoltSpawn(ItemStack ammunition, Vec3 velocity, CannonShot shot, Optional<RailShot> railShot) {

    private static final StreamCodec<FriendlyByteBuf, Vec3> VELOCITY_CODEC = StreamCodec.composite(
            ByteBufCodecs.DOUBLE, Vec3::x,
            ByteBufCodecs.DOUBLE, Vec3::y,
            ByteBufCodecs.DOUBLE, Vec3::z,
            Vec3::new);
    public static final StreamCodec<RegistryFriendlyByteBuf, CannonBoltSpawn> STREAM_CODEC = StreamCodec.composite(
            ItemStack.STREAM_CODEC, CannonBoltSpawn::ammunition,
            VELOCITY_CODEC, CannonBoltSpawn::velocity,
            CannonShot.STREAM_CODEC, CannonBoltSpawn::shot,
            ByteBufCodecs.optional(RailShot.STREAM_CODEC), CannonBoltSpawn::railShot,
            CannonBoltSpawn::new);

    public CannonBoltSpawn {
        if (!Double.isFinite(velocity.x) || !Double.isFinite(velocity.y) || !Double.isFinite(velocity.z)) {
            throw new IllegalArgumentException("Invalid projectile spawn velocity");
        }
    }
}
