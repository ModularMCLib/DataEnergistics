package com.fish_dan_.data_energistics.common.weapon.platform.combat;

import com.fish_dan_.data_energistics.common.combat.damage.DirectHealthDamage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;

import java.util.Comparator;
import java.util.function.Predicate;

/** Collects all segment intersections, resolving multipart bodies to their nearest accepted hit. */
public final class GunRayTrace {

    private GunRayTrace() {}

    /** Matches collider ray casting while treating unavailable chunks as opaque, without loading them. */
    public static BlockHitResult clip(Level level, Entity source, Vec3 from, Vec3 to) {
        ClipContext context = new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, source);
        Direction direction = Direction.getNearest(from.x - to.x, from.y - to.y, from.z - to.z);
        return BlockGetter.traverseBlocks(from, to, context, (ray, position) -> {
            if (!level.getChunkSource().hasChunk(position.getX() >> 4, position.getZ() >> 4))
                return new BlockHitResult(Vec3.atCenterOf(position), direction, position.immutable(), false);
            var state = level.getBlockState(position);
            return level.clipWithInteractionOverride(from, to, position, ray.getBlockShape(state, level, position), state);
        }, ray -> BlockHitResult.miss(to, direction, BlockPos.containing(to)));
    }

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
