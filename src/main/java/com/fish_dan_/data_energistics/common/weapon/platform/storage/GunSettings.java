package com.fish_dan_.data_energistics.common.weapon.platform.storage;

import com.fish_dan_.data_energistics.common.weapon.platform.model.GunAmmunition;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record GunSettings(boolean enabled, GunAmmunition ammunition, int targets, boolean supportSelf, boolean supportTeam) {

    public static final int PLAYERS = 1;
    public static final int HOSTILE = 2;
    public static final int FRIENDLY = 4;
    public static final int NEUTRAL = 8;
    public static final int OTHER = 16;
    public static final GunSettings DEFAULT = new GunSettings(true, GunAmmunition.AE, HOSTILE, true, true);
    public static final Codec<GunSettings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.fieldOf("enabled").forGetter(GunSettings::enabled),
            Codec.intRange(0, 4).xmap(id -> GunAmmunition.values()[id], Enum::ordinal).fieldOf("ammunition").forGetter(GunSettings::ammunition),
            Codec.intRange(0, 31).fieldOf("targets").forGetter(GunSettings::targets),
            Codec.BOOL.fieldOf("support_self").forGetter(GunSettings::supportSelf),
            Codec.BOOL.fieldOf("support_team").forGetter(GunSettings::supportTeam)).apply(instance, GunSettings::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, GunSettings> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);
}
