package com.fish_dan_.data_energistics.common.weapon.platform.storage;

import com.fish_dan_.data_energistics.common.weapon.platform.model.GunAmmunition;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** A failed shot returns its withdrawals only to the network that supplied them. */
public record ShotRefund(long frequency, GunAmmunition ammunition, double energy, long amount) {

    public static final Codec<ShotRefund> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.fieldOf("Frequency").forGetter(ShotRefund::frequency),
            Codec.intRange(0, 4).xmap(id -> GunAmmunition.values()[id], Enum::ordinal).fieldOf("Ammunition").forGetter(ShotRefund::ammunition),
            Codec.doubleRange(0, 100000).fieldOf("Energy").forGetter(ShotRefund::energy),
            Codec.intRange(0, 1000).xmap(Integer::longValue, Math::toIntExact).fieldOf("Amount").forGetter(ShotRefund::amount)).apply(instance, ShotRefund::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, ShotRefund> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);

    public ShotRefund {
        if (!Double.isFinite(energy) || energy < 0 || energy > 100000 || amount < 0 || amount > (ammunition == GunAmmunition.DATA_FLOW ? 1000 : ammunition.amount))
            throw new IllegalArgumentException("Invalid platform shot refund");
    }
}
