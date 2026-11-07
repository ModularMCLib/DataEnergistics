package com.fish_dan_.data_energistics.common.weapon.platform.storage;

import com.fish_dan_.data_energistics.common.weapon.platform.model.GunAmmunition;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import lombok.Builder;

@Builder(toBuilder = true)
public record GunConfiguration(boolean enabled, GunAmmunition ammunition, boolean supportSelf, boolean supportTeam,
                               boolean inheritTargets, int targetMask, boolean targetsInitialized) {

    public static final int PLAYERS = 1;
    public static final int HOSTILE = 2;
    public static final int FRIENDLY = 4;
    public static final int NEUTRAL = 8;
    public static final int OTHER = 16;
    public static final GunConfiguration DEFAULT = new GunConfiguration(true, GunAmmunition.AE, true, true, true, HOSTILE, false);
    public static final Codec<GunConfiguration> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.fieldOf("enabled").forGetter(GunConfiguration::enabled),
            Codec.intRange(0, 4).xmap(id -> GunAmmunition.values()[id], Enum::ordinal).fieldOf("ammunition").forGetter(GunConfiguration::ammunition),
            Codec.BOOL.fieldOf("support_self").forGetter(GunConfiguration::supportSelf),
            Codec.BOOL.fieldOf("support_team").forGetter(GunConfiguration::supportTeam),
            Codec.BOOL.fieldOf("inherit_targets").forGetter(GunConfiguration::inheritTargets),
            Codec.intRange(0, 31).fieldOf("target_mask").forGetter(GunConfiguration::targetMask),
            Codec.BOOL.fieldOf("targets_initialized").forGetter(GunConfiguration::targetsInitialized)).apply(instance, GunConfiguration::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, GunConfiguration> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);
}
