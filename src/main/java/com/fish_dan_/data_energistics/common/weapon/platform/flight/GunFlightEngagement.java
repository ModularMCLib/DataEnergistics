package com.fish_dan_.data_energistics.common.weapon.platform.flight;

import com.fish_dan_.data_energistics.common.weapon.platform.combat.GunFiringSolution;
import com.fish_dan_.data_energistics.common.weapon.platform.model.GunForm;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.UUID;

/** A flight's firing-position cache, progress tracking and temporary pursuit exclusions. Server thread only. */
@NullMarked
public final class GunFlightEngagement {

    private final Object2LongOpenHashMap<UUID> deferredUntil = new Object2LongOpenHashMap<>();
    private @Nullable Pursuit pursuit;

    public boolean available(UUID id, long now) {
        return now >= deferredUntil.getLong(id);
    }

    @Nullable
    public Vec3 destination(FloatingGunFlight gun, LivingEntity target, GunForm requestedForm, int slot, long now) {
        Vec3 center = gun.getBoundingBox().getCenter();
        Vec3 targetCenter = target.getBoundingBox().getCenter();
        Pursuit active = pursuit;
        if (active == null || !target.getUUID().equals(active.targetId) || requestedForm != active.form) {
            pursuit = active = new Pursuit(target.getUUID(), requestedForm, center, now);
        }
        if (center.distanceToSqr(active.progressPosition) >= .25) {
            active.progressPosition = center;
            active.progressedAt = now;
        }
        double distance = idealDistance(requestedForm);
        boolean firing = GunFiringSolution.aimed(requestedForm, center, target).clear(gun.level(), gun);
        double separation = center.distanceToSqr(targetCenter);
        if (firing) {
            active.startedAt = active.progressedAt = now;
            if (separation >= distance * distance * .3 && separation <= distance * distance * 2.25) {
                active.position = center;
                active.refreshAt = now + 10;
                active.targetPosition = targetCenter;
                return center;
            }
        } else if (now - active.progressedAt >= 80 || now - active.startedAt >= 600) {
            defer(active.targetId, now);
            return null;
        }
        if (active.position != null && targetCenter.distanceToSqr(active.targetPosition) < 4) {
            if (now < active.refreshAt) return active.position;
            if (FlightPathSearch.clear(gun, active.position, active.position) && GunFiringSolution.aimed(requestedForm, active.position, target).clear(gun.level(), gun)) {
                active.refreshAt = now + 20;
                return active.position;
            }
        }
        active.targetPosition = targetCenter;
        active.refreshAt = now + 20;
        Vec3 outward = center.subtract(targetCenter).multiply(1, 0, 1).normalize();
        if (outward.lengthSqr() < .5) outward = new Vec3(0, 0, 1);
        var neighbors = gun.level().getEntitiesOfClass(FloatingGunFlight.class, target.getBoundingBox().inflate(distance + 4, 10, distance + 4), other -> other != gun);
        var candidates = new ObjectArrayList<Position>();
        for (int ring = 0; ring < 3; ring++) {
            double radius = ring == 2 ? Math.min(2, distance * .3) : distance * (ring == 0 ? 1 : .6);
            for (int elevation = 0; elevation < 3; elevation++) for (int direction = 0; direction < 8; direction++) {
                double angle = direction * Math.PI / 4 + (slot - 3.5) * .04;
                double height = elevation == 0 ? 1.5 : elevation == 1 ? 0 : 4.5;
                Vec3 candidate = targetCenter.add(outward.yRot((float) angle).scale(radius)).add(0, height, 0);
                if (active.rejected.stream().anyMatch(point -> point.distanceToSqr(candidate) < 4) || !FlightPathSearch.clear(gun, candidate, candidate)) continue;
                double score = center.distanceToSqr(candidate) + elevation * 4 + ring * 2 + GunFlightSpacing.penalty(candidate, neighbors);
                candidates.add(new Position(candidate, score));
            }
        }
        candidates.sort(Comparator.comparingDouble(Position::score));
        Vec3 nearest = null;
        int windows = 0;
        // Examine nearby windows first; only a few swept routes are needed before yielding to incremental A*.
        for (Position candidate : candidates) {
            if (!GunFiringSolution.aimed(requestedForm, candidate.point(), target).clear(gun.level(), gun)) continue;
            if (nearest == null) nearest = candidate.point();
            if (FlightPathSearch.clear(gun, center, candidate.point())) return active.position = candidate.point();
            if (++windows == 6) break;
        }
        active.position = nearest == null && firing ? center : nearest;
        if (active.position == null) defer(active.targetId, now);
        return active.position;
    }

    public boolean pathFailed(long now) {
        Pursuit active = pursuit;
        if (active == null) return false;
        if (active.position != null) active.rejected.add(active.position);
        active.position = null;
        active.refreshAt = 0;
        if (active.rejected.size() >= 3) defer(active.targetId, now);
        return true;
    }

    public void stop() {
        pursuit = null;
    }

    private void defer(UUID targetId, long now) {
        deferredUntil.object2LongEntrySet().removeIf(entry -> now >= entry.getLongValue());
        deferredUntil.put(targetId, now + 80);
        stop();
    }

    private static double idealDistance(GunForm form) {
        return switch (form) {
            case NORMAL -> 10;
            case SNIPER -> 24;
            case PLASMA -> 8;
            case GRENADE -> 12;
            case MEDICAL -> 3;
        };
    }

    private record Position(Vec3 point, double score) {}

    private static final class Pursuit {

        private final UUID targetId;
        private final GunForm form;
        private final ObjectArrayList<Vec3> rejected = new ObjectArrayList<>();
        private Vec3 targetPosition = Vec3.ZERO;
        private @Nullable Vec3 position;
        private Vec3 progressPosition;
        private long refreshAt;
        private long progressedAt;
        private long startedAt;

        private Pursuit(UUID targetId, GunForm form, Vec3 center, long now) {
            this.targetId = targetId;
            this.form = form;
            progressPosition = center;
            startedAt = progressedAt = now;
        }
    }
}
