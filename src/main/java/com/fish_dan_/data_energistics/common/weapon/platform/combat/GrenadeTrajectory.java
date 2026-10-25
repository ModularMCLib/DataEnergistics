package com.fish_dan_.data_energistics.common.weapon.platform.combat;

import net.minecraft.world.phys.Vec3;

/** Discrete ballistic motion shared by server launch and the tactical HUD prediction. */
public record GrenadeTrajectory(Vec3 origin, Vec3 velocity, double targetTick) {

    public static final double GRAVITY = 0.05;

    public static GrenadeTrajectory aimed(Vec3 origin, Vec3 target) {
        Vec3 delta = target.subtract(origin);
        double ticks = Math.max(8, delta.horizontalDistance() / 0.8);
        return new GrenadeTrajectory(origin, new Vec3(delta.x / ticks, delta.y / ticks + GRAVITY / 2 * (ticks - 1), delta.z / ticks), ticks);
    }

    public Vec3 position(double tick) {
        return origin.add(velocity.scale(tick)).add(0, -GRAVITY / 2 * tick * (tick - 1), 0);
    }
}
