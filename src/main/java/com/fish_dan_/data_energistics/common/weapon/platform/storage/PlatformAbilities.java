package com.fish_dan_.data_energistics.common.weapon.platform.storage;

import com.fish_dan_.data_energistics.common.weapon.platform.model.PlatformAbility;
import com.fish_dan_.data_energistics.common.weapon.platform.model.ThermalPalette;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import lombok.Builder;

@Builder(toBuilder = true)
public record PlatformAbilities(int enabled, int scanRadius, int domainChunks, float maximumFlightSpeed, boolean permanentFlight, ThermalPalette thermalPalette, LowLightVision lowLight, float normalFlightSpeed, boolean inertiaCancellation, DisarmProtection equipmentProtection) {

    public static final PlatformAbilities DEFAULT = new PlatformAbilities(0, 384, 3, 1.2f, false);
    public static final Codec<PlatformAbilities> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            PlatformAbility.FLAGS_CODEC.fieldOf("enabled").forGetter(PlatformAbilities::enabled),
            Codec.intRange(16, 384).fieldOf("scan_radius").forGetter(PlatformAbilities::scanRadius),
            Codec.intRange(3, 5).validate(value -> value == 3 || value == 5 ? DataResult.success(value) : DataResult.error(() -> "Invalid domain size"))
                    .fieldOf("domain_chunks").forGetter(PlatformAbilities::domainChunks),
            Codec.floatRange(0.28f, 5).fieldOf("maximum_flight_speed").forGetter(PlatformAbilities::maximumFlightSpeed),
            Codec.BOOL.fieldOf("permanent_flight").forGetter(PlatformAbilities::permanentFlight),
            ThermalPalette.CODEC.optionalFieldOf("thermal_palette", ThermalPalette.WHITE_HOT).forGetter(PlatformAbilities::thermalPalette),
            LowLightVision.CODEC.optionalFieldOf("low_light", LowLightVision.DEFAULT).forGetter(PlatformAbilities::lowLight),
            Codec.floatRange(0.28f, 5).optionalFieldOf("normal_flight_speed", 0.28f).forGetter(PlatformAbilities::normalFlightSpeed),
            Codec.BOOL.optionalFieldOf("inertia_cancellation", true).forGetter(PlatformAbilities::inertiaCancellation),
            DisarmProtection.CODEC.optionalFieldOf("equipment_protection", DisarmProtection.DEFAULT).forGetter(PlatformAbilities::equipmentProtection))
            .apply(instance, PlatformAbilities::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, PlatformAbilities> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);

    public PlatformAbilities(int enabled, int scanRadius, int domainChunks, float maximumFlightSpeed, boolean permanentFlight) {
        this(enabled, scanRadius, domainChunks, maximumFlightSpeed, permanentFlight, ThermalPalette.WHITE_HOT);
    }

    public PlatformAbilities(int enabled, int scanRadius, int domainChunks, float maximumFlightSpeed, boolean permanentFlight, ThermalPalette thermalPalette) {
        this(enabled, scanRadius, domainChunks, maximumFlightSpeed, permanentFlight, thermalPalette, LowLightVision.DEFAULT);
    }

    public PlatformAbilities(int enabled, int scanRadius, int domainChunks, float maximumFlightSpeed, boolean permanentFlight, ThermalPalette thermalPalette, LowLightVision lowLight) {
        this(enabled, scanRadius, domainChunks, maximumFlightSpeed, permanentFlight, thermalPalette, lowLight, 0.28f, true);
    }

    public PlatformAbilities(int enabled, int scanRadius, int domainChunks, float maximumFlightSpeed, boolean permanentFlight, ThermalPalette thermalPalette, LowLightVision lowLight, float normalFlightSpeed, boolean inertiaCancellation) {
        this(enabled, scanRadius, domainChunks, maximumFlightSpeed, permanentFlight, thermalPalette, lowLight, normalFlightSpeed, inertiaCancellation, DisarmProtection.DEFAULT);
    }

    public boolean enabled(PlatformAbility ability) {
        return (enabled & ability.flag) != 0;
    }
}
