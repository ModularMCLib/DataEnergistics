package com.fish_dan_.data_energistics.common.weapon.platform.storage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import lombok.Builder;

/** Persisted image-intensifier controls. Saved data and RPC input validate their ranges at the boundary. */
@Builder(toBuilder = true)
public record LowLightVision(float gain, boolean automaticBrightness, float noise, boolean infraredIlluminator) {

    public static final float MIN_GAIN = 0.5f;
    public static final float MAX_GAIN = 8;
    public static final float MIN_NOISE = 0;
    public static final float MAX_NOISE = 100;
    public static final Codec<Float> GAIN_CODEC = Codec.floatRange(MIN_GAIN, MAX_GAIN);
    public static final Codec<Float> NOISE_CODEC = Codec.floatRange(MIN_NOISE, MAX_NOISE);
    public static final LowLightVision DEFAULT = new LowLightVision(2, true, 20, false);
    public static final Codec<LowLightVision> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            GAIN_CODEC.optionalFieldOf("gain", DEFAULT.gain()).forGetter(LowLightVision::gain),
            Codec.BOOL.optionalFieldOf("automatic_brightness", DEFAULT.automaticBrightness()).forGetter(LowLightVision::automaticBrightness),
            NOISE_CODEC.optionalFieldOf("noise", DEFAULT.noise()).forGetter(LowLightVision::noise),
            Codec.BOOL.optionalFieldOf("infrared_illuminator", DEFAULT.infraredIlluminator()).forGetter(LowLightVision::infraredIlluminator))
            .apply(instance, LowLightVision::new));
}
