package com.fish_dan_.data_energistics.common.entity.projectile.cannon;

import com.fish_dan_.data_energistics.common.item.powered.MatterConvergingCrossbowMode;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** A shot snapshots its firing mode, charge, and damage-speed input at release. */
public record CannonShot(MatterConvergingCrossbowMode mode, float charge, float speedSnapshot) {

    private static final Codec<MatterConvergingCrossbowMode> MODE_CODEC = Codec.STRING.comapFlatMap(
            CannonShot::decodeMode, MatterConvergingCrossbowMode::name);
    public static final Codec<CannonShot> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            MODE_CODEC.fieldOf("mode").forGetter(CannonShot::mode),
            Codec.floatRange(0, 1).fieldOf("charge").forGetter(CannonShot::charge),
            Codec.floatRange(0, Float.MAX_VALUE).fieldOf("speed").forGetter(CannonShot::speedSnapshot)).apply(instance, CannonShot::new));
    public static final StreamCodec<FriendlyByteBuf, CannonShot> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8.map(value -> decodeMode(value).getOrThrow(), MatterConvergingCrossbowMode::name), CannonShot::mode,
            ByteBufCodecs.FLOAT, CannonShot::charge,
            ByteBufCodecs.FLOAT, CannonShot::speedSnapshot,
            CannonShot::new);

    public static final float BASE_DAMAGE_SPEED = 3.15F;
    public static final float CROSSBOW_BASE_DAMAGE_MULTIPLIER = 3.0F;
    public static final CannonShot CROSSBOW = new CannonShot(MatterConvergingCrossbowMode.CROSSBOW, 1.0F, BASE_DAMAGE_SPEED);

    public CannonShot {
        if (!Float.isFinite(charge) || charge < 0 || charge > 1 || !Float.isFinite(speedSnapshot) || speedSnapshot < 0) {
            throw new IllegalArgumentException("Invalid cannon damage budget");
        }
    }

    public float damageScale() {
        return this.mode == MatterConvergingCrossbowMode.RAIL ? this.charge : 1.0F;
    }

    public float baseDamageMultiplier() {
        return this.mode == MatterConvergingCrossbowMode.CROSSBOW ? CROSSBOW_BASE_DAMAGE_MULTIPLIER : 1.0F;
    }

    /**
     * Converts the speed relevant to this mode into a damage multiplier. Rail projectiles use
     * their release snapshot because their physical flight speed is intentionally near-hitscan.
     */
    public float speedMultiplier(float currentSpeed) {
        float damageSpeed = switch (this.mode) {
            case CROSSBOW -> currentSpeed;
            case RAIL -> this.speedSnapshot;
            case GRENADE -> BASE_DAMAGE_SPEED;
        };
        return Float.isFinite(damageSpeed) ? Math.max(0.0F, damageSpeed) / BASE_DAMAGE_SPEED : 0.0F;
    }

    private static DataResult<MatterConvergingCrossbowMode> decodeMode(String name) {
        for (MatterConvergingCrossbowMode mode : MatterConvergingCrossbowMode.values()) {
            if (mode.name().equals(name)) return DataResult.success(mode);
        }
        return DataResult.error(() -> "Unknown cannon shot mode: " + name);
    }
}
