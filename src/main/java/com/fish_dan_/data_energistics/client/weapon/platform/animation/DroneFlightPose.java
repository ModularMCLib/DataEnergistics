package com.fish_dan_.data_energistics.client.weapon.platform.animation;

/**
 * Stateless visual response to one client tick of interpolated motion.
 * Angles are offsets from the authored wing pose, in degrees.
 */
public record DroneFlightPose(float forwardTilt, float sideTilt, float turnTilt, float liftTilt) {

    public static DroneFlightPose sample(double dx, double dy, double dz, float yaw, float previousYaw) {
        double radians = Math.toRadians(yaw);
        double forward = dz * Math.cos(radians) - dx * Math.sin(radians);
        double sideways = -dx * Math.cos(radians) - dz * Math.sin(radians);
        double turn = Math.IEEEremainder(yaw - previousYaw, 360);
        return new DroneFlightPose(limit(forward * 28, 14), limit(sideways * 32, 14),
                limit(turn * .7, 8), limit(dy * 24, 5));
    }

    public float pitch(boolean front, boolean left) {
        return -forwardTilt + (front ? -liftTilt : liftTilt) + (left ? turnTilt : -turnTilt) * .3f;
    }

    public float roll(boolean front) {
        return -sideTilt + (front ? turnTilt : -turnTilt);
    }

    public float sweep(boolean front, boolean left) {
        return (left ? -1 : 1) * Math.abs(forwardTilt) * .2f + (front ? turnTilt : -turnTilt) * .25f;
    }

    private static float limit(double value, double maximum) {
        return (float) Math.clamp(value, -maximum, maximum);
    }
}
