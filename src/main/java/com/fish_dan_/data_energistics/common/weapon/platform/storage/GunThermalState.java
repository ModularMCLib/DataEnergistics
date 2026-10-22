package com.fish_dan_.data_energistics.common.weapon.platform.storage;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Heat and shot cooldown travel with the gun so moving it between platforms cannot reset either. */
public record GunThermalState(double heat, long updatedAt, long readyAt, boolean overheated) {

    public static final GunThermalState COLD = new GunThermalState(0, 0, 0, false);
    public static final Codec<GunThermalState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.doubleRange(0, 100).fieldOf("heat").forGetter(GunThermalState::heat),
            Codec.LONG.fieldOf("updated_at").forGetter(GunThermalState::updatedAt),
            Codec.LONG.fieldOf("ready_at").forGetter(GunThermalState::readyAt),
            Codec.BOOL.fieldOf("overheated").forGetter(GunThermalState::overheated)).apply(instance, GunThermalState::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, GunThermalState> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);

    public double currentHeat(long now, double cooling) {
        return Math.max(0, heat - Math.max(0, now - updatedAt) * cooling);
    }
}
