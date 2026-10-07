package com.fish_dan_.data_energistics.entity.projectile.cannon;

import com.fish_dan_.data_energistics.item.powered.MatterConvergingCrossbowMode;

import net.minecraft.nbt.CompoundTag;

/** A shot snapshots its firing mode, charge, and damage-speed input at release. */
public record CannonShot(MatterConvergingCrossbowMode mode, float charge, float speedSnapshot) {

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

    public void save(CompoundTag parent) {
        if (this.mode == MatterConvergingCrossbowMode.CROSSBOW) return;
        CompoundTag tag = new CompoundTag();
        tag.putInt("Mode", this.mode.id());
        tag.putFloat("Charge", this.charge);
        tag.putFloat("SpeedSnapshot", this.speedSnapshot);
        parent.put("CannonShot", tag);
    }

    public static CannonShot load(CompoundTag parent) {
        if (!parent.contains("CannonShot", 10)) return CROSSBOW;
        CompoundTag tag = parent.getCompound("CannonShot");
        return new CannonShot(MatterConvergingCrossbowMode.fromId(tag.getInt("Mode")), tag.getFloat("Charge"), tag.getFloat("SpeedSnapshot"));
    }
}
