package com.fish_dan_.data_energistics.network.meteorite;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.level.ChunkPos;

final class DataMeteoritePayloadCodecs {

    static final StreamCodec<RegistryFriendlyByteBuf, ChunkPos> CHUNK_POSITION = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, position -> position.x,
            ByteBufCodecs.VAR_INT, position -> position.z,
            ChunkPos::new);

    private DataMeteoritePayloadCodecs() {}
}
