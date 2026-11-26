package com.fish_dan_.data_energistics.client.weapon.platform.animation;

/** Model-local gimbal angles; stops keep the barrel below the airframe and inside its yaw travel. */
public record DroneWeaponPose(float yaw, float pitch) {

    public static DroneWeaponPose sample(float hullYaw, float aimYaw, float aimPitch) {
        float relative = (hullYaw - aimYaw) % 360;
        if (relative >= 180) relative -= 360;
        if (relative < -180) relative += 360;
        return new DroneWeaponPose(Math.clamp(relative, -55, 55), -Math.clamp(aimPitch, -15, 75));
    }
}
