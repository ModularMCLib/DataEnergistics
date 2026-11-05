package com.fish_dan_.data_energistics.common.weapon.platform.storage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import lombok.Builder;

/** Persisted image-intensifier controls. Saved data and RPC input validate their ranges at the boundary. */
@Builder(toBuilder = true)
public record LowLightSettings(float gain, boolean automaticBrightness, int noise, boolean infraredIlluminator) {

    public static final LowLightSettings DEFAULT = new LowLightSettings(2, true, 20, false);
    public static final Codec<LowLightSettings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.floatRange(0.5f, 8).optionalFieldOf("gain", DEFAULT.gain()).forGetter(LowLightSettings::gain),
            Codec.BOOL.optionalFieldOf("automatic_brightness", DEFAULT.automaticBrightness()).forGetter(LowLightSettings::automaticBrightness),
            Codec.intRange(0, 100).optionalFieldOf("noise", DEFAULT.noise()).forGetter(LowLightSettings::noise),
            Codec.BOOL.optionalFieldOf("infrared_illuminator", DEFAULT.infraredIlluminator()).forGetter(LowLightSettings::infraredIlluminator))
            .apply(instance, LowLightSettings::new));
}
