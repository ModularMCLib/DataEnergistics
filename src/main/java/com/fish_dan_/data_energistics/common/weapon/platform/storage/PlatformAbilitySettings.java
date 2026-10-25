package com.fish_dan_.data_energistics.common.weapon.platform.storage;

import com.fish_dan_.data_energistics.common.weapon.platform.model.PlatformAbility;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import lombok.Builder;

@Builder(toBuilder = true)
public record PlatformAbilitySettings(int enabled, int shieldColor, int scanRadius, int domainChunks) {

    public static final PlatformAbilitySettings DEFAULT = new PlatformAbilitySettings(0, 0x99696d88, 384, 3);
    public static final Codec<PlatformAbilitySettings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            PlatformAbility.FLAGS_CODEC.fieldOf("enabled").forGetter(PlatformAbilitySettings::enabled),
            Codec.INT.fieldOf("shield_color").forGetter(PlatformAbilitySettings::shieldColor),
            Codec.intRange(16, 384).fieldOf("scan_radius").forGetter(PlatformAbilitySettings::scanRadius),
            Codec.intRange(3, 5).validate(value -> value == 3 || value == 5 ? DataResult.success(value) : DataResult.error(() -> "Invalid domain size"))
                    .fieldOf("domain_chunks").forGetter(PlatformAbilitySettings::domainChunks))
            .apply(instance, PlatformAbilitySettings::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, PlatformAbilitySettings> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);

    public boolean enabled(PlatformAbility ability) {
        return (enabled & ability.flag) != 0;
    }
}
