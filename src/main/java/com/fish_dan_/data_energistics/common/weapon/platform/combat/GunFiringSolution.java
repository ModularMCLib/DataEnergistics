package com.fish_dan_.data_energistics.common.weapon.platform.combat;

import com.fish_dan_.data_energistics.common.weapon.platform.model.GunForm;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.NullMarked;

/** One launch solution shared by target selection, firing-position search, aiming and projectile flight. */
@NullMarked
public record GunFiringSolution(GunForm form, Vec3 origin, Vec3 impact, Vec3 velocity, double travelTicks) {

    public static GunFiringSolution aimed(GunForm form, Vec3 origin, LivingEntity target) {
        Vec3 motion = target.getKnownMovement();
        if (target.onGround()) motion = motion.multiply(1, 0, 1);
        return aimed(form, origin, target.getBoundingBox().getCenter(), motion);
    }

    private static GunFiringSolution aimed(GunForm form, Vec3 origin, Vec3 target, Vec3 motion) {
        if (form == GunForm.GRENADE) {
            GrenadeTrajectory trajectory = GrenadeTrajectory.aimed(origin, target);
            Vec3 impact = target;
            for (int iteration = 0; iteration < 8; iteration++) {
                impact = target.add(motion.scale(trajectory.targetTick()));
                GrenadeTrajectory next = GrenadeTrajectory.aimed(origin, impact);
                double difference = Math.abs(next.targetTick() - trajectory.targetTick());
                trajectory = next;
                if (difference < .01 || trajectory.targetTick() > lifetime(form)) break;
            }
            return new GunFiringSolution(form, origin, impact, trajectory.velocity(), trajectory.targetTick());
        }
        Vec3 delta = target.subtract(origin);
        if (form == GunForm.SNIPER) return new GunFiringSolution(form, origin, target, delta.normalize(), 0);
        double speed = form == GunForm.NORMAL ? 3 : form == GunForm.MEDICAL ? 1.4 : .7;
        double ticks = form == GunForm.MEDICAL ? delta.length() / speed : intercept(delta, motion, speed);
        Vec3 impact = Double.isFinite(ticks) ? target.add(motion.scale(form == GunForm.MEDICAL ? 0 : ticks)) : target;
        return new GunFiringSolution(form, origin, impact, impact.subtract(origin).normalize().scale(speed), ticks);
    }

    private static double intercept(Vec3 delta, Vec3 motion, double speed) {
        double c = delta.lengthSqr();
        if (c < 1.0e-8) return 0;
        double a = motion.lengthSqr() - speed * speed;
        double b = 2 * delta.dot(motion);
        if (Math.abs(a) < 1.0e-8) return b < -1.0e-8 ? -c / b : Double.POSITIVE_INFINITY;
        double discriminant = b * b - 4 * a * c;
        if (discriminant < 0) return Double.POSITIVE_INFINITY;
        double root = Math.sqrt(discriminant);
        double first = (-b - root) / (2 * a), second = (-b + root) / (2 * a);
        if (first > 0 && second > 0) return Math.min(first, second);
        return Math.max(first, second) > 0 ? Math.max(first, second) : Double.POSITIVE_INFINITY;
    }

    public static int lifetime(GunForm form) {
        return form == GunForm.PLASMA ? 100 : form == GunForm.SNIPER ? 3 : 200;
    }

    /** Uses the same discrete gravity and collider segments as the actual grenade, without loading chunks. */
    public boolean clear(Level level, Entity source) {
        if (Math.ceil(travelTicks) >= lifetime(form)) return false;
        if (form != GunForm.GRENADE) return GunRayTrace.clip(level, source, origin, impact).getType() == HitResult.Type.MISS;
        Vec3 from = origin;
        for (int tick = 1; tick <= Math.ceil(travelTicks); tick++) {
            Vec3 to = position(Math.min(tick, travelTicks));
            if (GunRayTrace.clip(level, source, from, to).getType() != HitResult.Type.MISS) return false;
            from = to;
        }
        return true;
    }

    private Vec3 position(double tick) {
        Vec3 point = origin.add(velocity.scale(tick));
        return form == GunForm.GRENADE ? point.add(0, -GrenadeTrajectory.GRAVITY / 2 * tick * (tick - 1), 0) : point;
    }
}
