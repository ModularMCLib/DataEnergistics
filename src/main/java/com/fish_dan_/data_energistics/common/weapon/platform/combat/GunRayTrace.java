package com.fish_dan_.data_energistics.common.weapon.platform.combat;

import com.fish_dan_.data_energistics.common.combat.damage.DirectHealthDamage;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;

import java.util.Comparator;
import java.util.function.Predicate;

/** Collects all segment intersections, resolving multipart bodies to their nearest accepted hit. */
public final class GunRayTrace {

    private GunRayTrace() {}

    public static ObjectList<Hit> collect(ServerLevel level, Entity source, Vec3 from, Vec3 to, double radius, Predicate<LivingEntity> accepted) {
        var intersections = new Object2ObjectOpenHashMap<LivingEntity, Vec3>();
        for (Entity candidate : level.getEntities(source, new AABB(from, to).inflate(radius + 1))) {
            LivingEntity target = DirectHealthDamage.target(candidate);
            if (target == null || !accepted.test(target)) continue;
            AABB bounds = candidate.getBoundingBox().inflate(radius);
            Vec3 point = bounds.contains(from) ? from : bounds.clip(from, to).orElse(null);
            if (point == null) continue;
            intersections.merge(target, point, (previous, next) -> from.distanceToSqr(previous) <= from.distanceToSqr(next) ? previous : next);
        }
        var hits = new ObjectArrayList<Hit>(intersections.size());
        for (var entry : intersections.object2ObjectEntrySet()) hits.add(new Hit(entry.getKey(), entry.getValue()));
        hits.sort(Comparator.comparingDouble(hit -> from.distanceToSqr(hit.position())));
        return hits;
    }

    public record Hit(LivingEntity target, Vec3 position) {}
}
