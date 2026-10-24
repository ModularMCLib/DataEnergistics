package com.fish_dan_.data_energistics.common.weapon.platform.shield;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public record ShieldState(int charge, long overloadedUntil, boolean active) {
    public static final ShieldState EMPTY = new ShieldState(0, 0, false);
    public static final Codec<ShieldState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.intRange(0, 100000).fieldOf("charge").forGetter(ShieldState::charge),
            Codec.LONG.fieldOf("overloaded_until").forGetter(ShieldState::overloadedUntil),
            Codec.BOOL.fieldOf("active").forGetter(ShieldState::active)).apply(instance, ShieldState::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, ShieldState> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);
}
