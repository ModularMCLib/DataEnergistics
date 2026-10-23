package com.fish_dan_.data_energistics.common.item.powered.lightsaber;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Shared model dimensions and the horizontal blade's six-degree sweep over one server tick. */
public final class LightSaberOrbitGeometry {

    public static final float RADIUS = 2.2F;
    public static final float HEIGHT = 1.0F;
    public static final float SCALE = 0.8F;
    public static final float RING_SEPARATION = 0.8F;
    // Actual on.obj bounds after ItemRenderer subtracts 0.5, then applies the orbit scale.
    private static final double INNER_RADIUS = RADIUS - 0.384219375 * SCALE;
    private static final double OUTER_RADIUS = RADIUS + 2.0555125 * SCALE;
    private static final double HALF_WIDTH = 0.11902125 * SCALE;
    private static final double HALF_HEIGHT = 0.054776875 * SCALE;

    private LightSaberOrbitGeometry() {}

    public static AABB sweptBounds(Vec3 center, double angle, int ring) {
        int direction = ring == 0 ? 1 : -1;
        AABB bounds = new AABB(point(center, angle - direction * 6, INNER_RADIUS, ring), point(center, angle - direction * 6, OUTER_RADIUS, ring));
        for (int step = 1; step <= 6; step++) {
            double sample = angle - direction * (6 - step);
            bounds = bounds.minmax(new AABB(point(center, sample, INNER_RADIUS, ring), point(center, sample, OUTER_RADIUS, ring)));
        }
        return bounds.inflate(HALF_WIDTH, HALF_HEIGHT, HALF_WIDTH);
    }

    public static boolean intersects(AABB target, Vec3 center, double angle, int ring) {
        AABB expanded = target.inflate(HALF_WIDTH, HALF_HEIGHT, HALF_WIDTH);
        int direction = ring == 0 ? 1 : -1;
        for (int step = 0; step <= 6; step++) {
            double sample = angle - direction * (6 - step);
            Vec3 start = point(center, sample, INNER_RADIUS, ring);
            Vec3 end = point(center, sample, OUTER_RADIUS, ring);
            if (expanded.contains(start) || expanded.clip(start, end).isPresent()) return true;
        }
        return false;
    }

    private static Vec3 point(Vec3 center, double angle, double radius, int ring) {
        double radians = Math.toRadians(angle);
        return center.add(Math.cos(radians) * radius, HEIGHT + ring * RING_SEPARATION, -Math.sin(radians) * radius);
    }
}
