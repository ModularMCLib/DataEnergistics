package com.fish_dan_.data_energistics.client.weapon.platform.vision.sonar;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import com.mojang.blaze3d.vertex.BufferBuilder;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.doubles.DoubleComparators;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import lombok.RequiredArgsConstructor;

/** Builds section-local feature edges from the union of loaded block shapes, including neighboring sections. */
@RequiredArgsConstructor
public final class SonarSurfaceContours {

    private static final double EPSILON = 1.0e-5;
    private final ClientLevel level;
    private final BlockPos origin;
    private final Long2ObjectOpenHashMap<VoxelShape> shapes = new Long2ObjectOpenHashMap<>();
    private final Reference2ObjectOpenHashMap<VoxelShape, ObjectList<AABB>> boxes = new Reference2ObjectOpenHashMap<>();
    private final ObjectOpenHashSet<Edge> edges = new ObjectOpenHashSet<>();
    private final DoubleArrayList cuts = new DoubleArrayList();

    public void add(BlockPos position, VoxelShape shape) {
        shapes.put(position.asLong(), shape);
        shape.forAllEdges((x1, y1, z1, x2, y2, z2) -> {
            Direction.Axis axis = x1 != x2 ? Direction.Axis.X : y1 != y2 ? Direction.Axis.Y : Direction.Axis.Z;
            double start = axis.choose(x1 + position.getX(), y1 + position.getY(), z1 + position.getZ());
            double end = axis.choose(x2 + position.getX(), y2 + position.getY(), z2 + position.getZ());
            double u = axis == Direction.Axis.X ? y1 + position.getY() : x1 + position.getX();
            double v = axis == Direction.Axis.Z ? y1 + position.getY() : z1 + position.getZ();
            collect(position, shape, axis, Math.min(start, end), Math.max(start, end), u, v);
        });
    }

    private void collect(BlockPos source, VoxelShape shape, Direction.Axis axis, double start, double end, double u, double v) {
        cuts.clear();
        cuts.add(start);
        cuts.add(end);
        // A neighboring slab or stair can cover only part of an edge. Split at its shape coordinates
        // before sampling the four quadrants, rather than deciding from one whole-block midpoint.
        for (int cell = Mth.floor(start); cell <= Mth.floor(end - EPSILON); cell++) {
            if (cell > start) cuts.add(cell);
            double along = (Math.max(start, cell) + Math.min(end, cell + 1)) * 0.5;
            for (int side = 0; side < 4; side++) {
                BlockPos neighbor = position(axis, along, u + ((side & 1) == 0 ? -EPSILON : EPSILON), v + ((side & 2) == 0 ? -EPSILON : EPSILON));
                for (double coordinate : shapeAt(neighbor).getCoords(axis)) {
                    double cut = axis.choose(neighbor.getX(), neighbor.getY(), neighbor.getZ()) + coordinate;
                    if (cut > start && cut < end) cuts.add(cut);
                }
            }
        }
        cuts.sort(DoubleComparators.NATURAL_COMPARATOR);
        for (int index = 1; index < cuts.size(); index++) {
            double a = cuts.getDouble(index - 1), b = cuts.getDouble(index);
            if (b - a < EPSILON) continue;
            int filled = 0;
            for (int side = 0; side < 4; side++) {
                double across = u + ((side & 1) == 0 ? -EPSILON : EPSILON);
                double vertical = v + ((side & 2) == 0 ? -EPSILON : EPSILON);
                if (occupied(source, shape, axis, (a + b) * 0.5, across, vertical)) filled |= 1 << side;
            }
            // Two adjacent quadrants describe a flat continuous surface. Only corners, concave
            // joins and diagonal contacts have a contour; fully filled rock has none.
            if (filled == 0 || filled == 15 || filled == 3 || filled == 5 || filled == 10 || filled == 12) continue;
            edges.add(switch (axis) {
                case X -> new Edge((float) (a - origin.getX()), (float) (u - origin.getY()), (float) (v - origin.getZ()), (float) (b - origin.getX()), (float) (u - origin.getY()), (float) (v - origin.getZ()));
                case Y -> new Edge((float) (u - origin.getX()), (float) (a - origin.getY()), (float) (v - origin.getZ()), (float) (u - origin.getX()), (float) (b - origin.getY()), (float) (v - origin.getZ()));
                case Z -> new Edge((float) (u - origin.getX()), (float) (v - origin.getY()), (float) (a - origin.getZ()), (float) (u - origin.getX()), (float) (v - origin.getY()), (float) (b - origin.getZ()));
            });
        }
    }

    private boolean occupied(BlockPos source, VoxelShape shape, Direction.Axis axis, double along, double u, double v) {
        double x = axis == Direction.Axis.X ? along : u;
        double y = axis == Direction.Axis.Y ? along : axis == Direction.Axis.X ? u : v;
        double z = axis == Direction.Axis.Z ? along : v;
        if (contains(shape, x - source.getX(), y - source.getY(), z - source.getZ())) return true;
        BlockPos position = BlockPos.containing(x, y, z);
        return contains(shapeAt(position), x - position.getX(), y - position.getY(), z - position.getZ());
    }

    private boolean contains(VoxelShape shape, double x, double y, double z) {
        for (AABB box : boxes.computeIfAbsent(shape, (VoxelShape key) -> new ObjectArrayList<>(key.toAabbs()))) if (box.contains(x, y, z)) return true;
        return false;
    }

    private VoxelShape shapeAt(BlockPos position) {
        return shapes.computeIfAbsent(position.asLong(), key -> {
            var chunk = level.getChunkSource().getChunk(position.getX() >> 4, position.getZ() >> 4, false);
            if (chunk == null) return Shapes.empty();
            BlockState state = chunk.getBlockState(position);
            VoxelShape shape = state.getShape(level, position);
            var fluid = state.getFluidState();
            return shape.isEmpty() && !fluid.isEmpty() ? Shapes.box(0, 0, 0, 1, fluid.getHeight(level, position), 1) : shape;
        });
    }

    private static BlockPos position(Direction.Axis axis, double along, double u, double v) {
        return switch (axis) {
            case X -> BlockPos.containing(along, u, v);
            case Y -> BlockPos.containing(u, along, v);
            case Z -> BlockPos.containing(u, v, along);
        };
    }

    public void write(BufferBuilder builder) {
        for (Edge edge : edges) {
            builder.addVertex(edge.x1(), edge.y1(), edge.z1()).setColor(65, 200, 240, 210);
            builder.addVertex(edge.x2(), edge.y2(), edge.z2()).setColor(65, 200, 240, 210);
        }
    }

    private record Edge(float x1, float y1, float z1, float x2, float y2, float z2) {}
}
