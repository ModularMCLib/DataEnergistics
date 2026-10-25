package com.fish_dan_.data_energistics.common.weapon.platform.flight;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectHeapPriorityQueue;
import it.unimi.dsi.fastutil.objects.ObjectList;
import org.jspecify.annotations.Nullable;

/** Incremental six-neighbor A*: swept body checks prevent corner cutting and unloaded-chunk access. */
final class FlightPathSearch {

    private final FloatingGunFlight gun;
    private final Vec3 goal;
    private final long start;
    private final ObjectHeapPriorityQueue<Node> open = new ObjectHeapPriorityQueue<>();
    private final Long2DoubleOpenHashMap costs = new Long2DoubleOpenHashMap();
    private final Long2LongOpenHashMap parents = new Long2LongOpenHashMap();
    private final LongOpenHashSet closed = new LongOpenHashSet();
    private @Nullable ObjectList<Vec3> result;
    private boolean complete;

    FlightPathSearch(FloatingGunFlight gun, Vec3 goal) {
        this.gun = gun;
        this.goal = goal;
        start = BlockPos.containing(gun.getBoundingBox().getCenter()).asLong();
        costs.defaultReturnValue(Double.POSITIVE_INFINITY);
        costs.put(start, 0);
        open.enqueue(new Node(start, 0, center(start).distanceTo(goal)));
    }

    boolean advance(int budget) {
        if (gun.isRemoved()) return complete = true;
        while (budget-- > 0 && !open.isEmpty()) {
            Node node = open.dequeue();
            if (!closed.add(node.position())) continue;
            Vec3 from = center(node.position());
            if (from.distanceToSqr(goal) < 4 && clear(gun, from, goal)) {
                var reversed = new ObjectArrayList<Vec3>();
                reversed.add(goal);
                long cursor = node.position();
                while (cursor != start) {
                    reversed.add(center(cursor));
                    cursor = parents.get(cursor);
                }
                result = new ObjectArrayList<>(reversed.size());
                for (int index = reversed.size() - 1; index >= 0; index--) result.add(reversed.get(index));
                return complete = true;
            }
            if (closed.size() >= 16384) return complete = true;
            BlockPos position = BlockPos.of(node.position());
            for (Direction direction : Direction.values()) {
                BlockPos next = position.relative(direction);
                long key = next.asLong();
                double cost = node.cost() + 1;
                if (closed.contains(key) || cost >= costs.get(key) || !clear(gun, from, Vec3.atCenterOf(next))) continue;
                costs.put(key, cost);
                parents.put(key, node.position());
                open.enqueue(new Node(key, cost, cost + Vec3.atCenterOf(next).distanceTo(goal)));
            }
        }
        return complete = open.isEmpty();
    }

    static boolean clear(FloatingGunFlight gun, Vec3 from, Vec3 to) {
        double halfWidth = gun.getBbWidth() / 2, halfHeight = gun.getBbHeight() / 2;
        int steps = Math.max(1, (int) Math.ceil(from.distanceTo(to)));
        Vec3 start = from;
        for (int step = 1; step <= steps; step++) {
            Vec3 end = from.lerp(to, (double) step / steps);
            AABB box = AABB.ofSize(start, gun.getBbWidth(), gun.getBbHeight(), gun.getBbWidth()).expandTowards(end.subtract(start));
            if (box.minY < gun.level().getMinBuildHeight() || box.maxY > gun.level().getMaxBuildHeight()) return false;
            for (int x = BlockPos.containing(box.minX, 0, 0).getX() >> 4; x <= BlockPos.containing(box.maxX, 0, 0).getX() >> 4; x++)
                for (int z = BlockPos.containing(0, 0, box.minZ).getZ() >> 4; z <= BlockPos.containing(0, 0, box.maxZ).getZ() >> 4; z++)
                    if (!gun.level().getChunkSource().hasChunk(x, z)) return false;
            for (var shape : gun.level().getBlockCollisions(gun, box)) for (var obstacle : shape.toAabbs()) {
                var expanded = obstacle.inflate(halfWidth, halfHeight, halfWidth);
                if (expanded.contains(start) || expanded.contains(end) || expanded.clip(start, end).isPresent()) return false;
            }
            start = end;
        }
        return true;
    }

    boolean complete() {
        return complete;
    }

    void cancel() {
        complete = true;
    }

    @Nullable
    ObjectList<Vec3> result() {
        return result;
    }

    Vec3 goal() {
        return goal;
    }

    private static Vec3 center(long position) {
        return Vec3.atCenterOf(BlockPos.of(position));
    }

    private record Node(long position, double cost, double score) implements Comparable<Node> {

        @Override
        public int compareTo(Node other) {
            return Double.compare(score, other.score);
        }
    }
}
