package com.fish_dan_.data_energistics.common.weapon.platform.flight;

/** Minecraft yaw/pitch in degrees for a server-selected target direction. */
public record GunAim(float yaw, float pitch) {

    /** A vertical target preserves the hull bearing; a coincident target levels the barrel. */
    public static GunAim fromDirection(double x, double y, double z, float previousYaw) {
        double horizontal = Math.hypot(x, z);
        float yaw = horizontal > 1e-6 ? (float) Math.toDegrees(Math.atan2(-x, z)) : previousYaw;
        return new GunAim(yaw, (float) -Math.toDegrees(Math.atan2(y, horizontal)));
    }
}
