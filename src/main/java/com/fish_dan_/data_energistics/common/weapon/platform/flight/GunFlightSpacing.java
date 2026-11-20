package com.fish_dan_.data_energistics.common.weapon.platform.flight;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.NullMarked;

/** Local clearance between airframes, including the next few ticks of converging motion. */
@NullMarked
public final class GunFlightSpacing {

    private static final double CLEARANCE = 2.4;
    private Vec3 requestedStation = Vec3.ZERO;
    private Vec3 followStation = Vec3.ZERO;
    private long refreshAt;

    /** Keeps dispersed indoor stations in free space instead of repeatedly trying to path into a wall. */
    public Vec3 following(FloatingGunFlight gun, Vec3 desired, Vec3 fallback) {
        long now = gun.level().getGameTime();
        if (now < refreshAt && desired.distanceToSqr(requestedStation) < .25) return followStation;
        requestedStation = desired;
        refreshAt = now + 10;
        if (FlightPathSearch.clear(gun, desired, desired)) return followStation = desired;
        var neighbors = gun.level().getEntitiesOfClass(FloatingGunFlight.class, AABB.ofSize(desired, 12, 8, 12), other -> other != gun);
        Vec3 selected = fallback;
        double best = Double.POSITIVE_INFINITY;
        for (int height = -2; height <= 1; height++) for (int ring = 0; ring <= 2; ring++) {
            for (int direction = 0; direction < (ring == 0 ? 1 : 8); direction++) {
                double angle = direction * Math.PI / 4;
                Vec3 candidate = desired.add(Math.cos(angle) * ring * 1.5, height, Math.sin(angle) * ring * 1.5);
                if (!FlightPathSearch.clear(gun, candidate, candidate)) continue;
                double score = candidate.distanceToSqr(desired) + penalty(candidate, neighbors);
                if (score < best) {
                    best = score;
                    selected = candidate;
                }
            }
        }
        return followStation = selected;
    }

    public static Vec3 steer(FloatingGunFlight gun, Vec3 preferred) {
        Vec3 center = gun.getBoundingBox().getCenter();
        Vec3 separation = Vec3.ZERO;
        for (FloatingGunFlight other : gun.level().getEntitiesOfClass(FloatingGunFlight.class, gun.getBoundingBox().inflate(5), entity -> entity != gun)) {
            Vec3 gap = center.subtract(other.getBoundingBox().getCenter());
            double distance = gap.length();
            if (distance < CLEARANCE) {
                Vec3 away = distance < 1.0e-4 ? new Vec3(gun.getId() < other.getId() ? -1 : 1, 0, 0) : gap.scale(1 / distance);
                separation = separation.add(away.scale((CLEARANCE - distance) / CLEARANCE * .22));
            }
            Vec3 relative = preferred.subtract(other.getDeltaMovement());
            if (gap.dot(relative) < 0 && gap.add(relative.scale(4)).lengthSqr() < CLEARANCE * CLEARANCE) {
                Vec3 sidestep = new Vec3(-preferred.z, 0, preferred.x);
                if (sidestep.lengthSqr() < 1.0e-8) sidestep = new Vec3(gun.getId() < other.getId() ? -1 : 1, 0, 0);
                else sidestep = sidestep.normalize();
                separation = separation.add(sidestep.scale(.14));
            }
        }
        Vec3 motion = preferred.add(separation);
        if (motion.lengthSqr() > .55 * .55) motion = motion.normalize().scale(.55);
        return FlightPathSearch.clear(gun, center, center.add(motion.scale(2))) ? motion : preferred;
    }

    public static double penalty(Vec3 station, Iterable<FloatingGunFlight> neighbors) {
        double penalty = 0;
        for (FloatingGunFlight neighbor : neighbors) {
            double distance = station.distanceTo(neighbor.getBoundingBox().getCenter());
            if (distance < CLEARANCE + 1) penalty += Math.pow(CLEARANCE + 1 - distance, 2) * 20;
        }
        return penalty;
    }
}
