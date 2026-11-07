package com.fish_dan_.data_energistics.common.weapon.platform.storage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import lombok.Builder;

/** Persisted image-intensifier controls. Saved data and RPC input validate their ranges at the boundary. */
@Builder(toBuilder = true)
public record LowLightSettings(float gain, boolean automaticBrightness, float noise, boolean infraredIlluminator) {

    public static final float MIN_GAIN = 0.5f;
    public static final float MAX_GAIN = 8;
    public static final float MIN_NOISE = 0;
    public static final float MAX_NOISE = 100;
    public static final Codec<Float> GAIN_CODEC = Codec.floatRange(MIN_GAIN, MAX_GAIN);
    public static final Codec<Float> NOISE_CODEC = Codec.floatRange(MIN_NOISE, MAX_NOISE);
    public static final LowLightSettings DEFAULT = new LowLightSettings(2, true, 20, false);
    public static final Codec<LowLightSettings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            GAIN_CODEC.optionalFieldOf("gain", DEFAULT.gain()).forGetter(LowLightSettings::gain),
            Codec.BOOL.optionalFieldOf("automatic_brightness", DEFAULT.automaticBrightness()).forGetter(LowLightSettings::automaticBrightness),
            NOISE_CODEC.optionalFieldOf("noise", DEFAULT.noise()).forGetter(LowLightSettings::noise),
            Codec.BOOL.optionalFieldOf("infrared_illuminator", DEFAULT.infraredIlluminator()).forGetter(LowLightSettings::infraredIlluminator))
            .apply(instance, LowLightSettings::new));
}
