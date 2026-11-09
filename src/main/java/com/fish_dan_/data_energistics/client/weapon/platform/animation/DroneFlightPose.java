package com.fish_dan_.data_energistics.client.weapon.platform.animation;

/**
 * Thrust-vector response to interpolated velocity and acceleration in world space.
 * Angles are offsets from the authored wing pose, in degrees.
 */
public record DroneFlightPose(float forwardTilt, float sideTilt, float turnTilt, float liftTilt) {

    public static DroneFlightPose sample(double dx, double dy, double dz, double ax, double ay, double az, float yaw, float turnRate) {
        double radians = Math.toRadians(yaw);
        double forward = dz * Math.cos(radians) - dx * Math.sin(radians);
        double sideways = -dx * Math.cos(radians) - dz * Math.sin(radians);
        double forwardAcceleration = az * Math.cos(radians) - ax * Math.sin(radians);
        double sideAcceleration = -ax * Math.cos(radians) - az * Math.sin(radians);
        return new DroneFlightPose(limit(forward * 32 + forwardAcceleration * 120, 24),
                limit(sideways * 32 + sideAcceleration * 120, 22), limit(turnRate * .75, 9), limit(dy * 12 + ay * 35, 5));
    }

    public float bodyPitch() {
        return -forwardTilt * .65f;
    }

    public float bodyRoll() {
        return limit(-sideTilt * .65f - turnTilt * .45f, 18);
    }

    public float pitch(boolean front, boolean left) {
        return -forwardTilt * .6f + (front ? -liftTilt : liftTilt) + (left ? turnTilt : -turnTilt) * .2f + (left ? -sideTilt : sideTilt) * .12f;
    }

    public float roll(boolean front, boolean left) {
        return -sideTilt * .55f + (front ? turnTilt : -turnTilt) * .5f + (left ? -liftTilt : liftTilt) * .35f;
    }

    public float sweep(boolean front, boolean left) {
        return (left ? -1 : 1) * (forwardTilt * .18f + liftTilt * .2f) + (front ? turnTilt : -turnTilt) * .25f + (front ? -sideTilt : sideTilt) * .08f;
    }

    private static float limit(double value, double maximum) {
        return (float) Math.clamp(value, -maximum, maximum);
    }
}
