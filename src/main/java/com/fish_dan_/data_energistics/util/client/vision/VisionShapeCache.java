package com.fish_dan_.data_energistics.util.client.vision;

import com.fish_dan_.data_energistics.mixin.minecraft.client.render.platform.PlatformVisionShapeAccessor;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.DiscreteVoxelShape;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import it.unimi.dsi.fastutil.doubles.DoubleList;
import it.unimi.dsi.fastutil.objects.Reference2ObjectLinkedOpenHashMap;
import org.jspecify.annotations.Nullable;

/** Client-thread identity cache. Both coordinate copying and grid sampling yield to the tick budget. */
@OnlyIn(Dist.CLIENT)
public final class VisionShapeCache {

    private static final long MAX_CELLS = 65_536;
    private static final int MAX_COORDINATES = 4_096;
    private static final int MAX_CACHED_SHAPES = 128;
    private final Reference2ObjectLinkedOpenHashMap<VoxelShape, Capture> shapes = new Reference2ObjectLinkedOpenHashMap<>();

    public @Nullable VisionShape capture(VoxelShape shape, long deadline) {
        if (shape == Shapes.block()) return VisionShape.BLOCK;
        if (shape == Shapes.empty()) return VisionShape.EMPTY;
        if (!shapes.containsKey(shape)) {
            shapes.put(shape, new Capture(shape));
            if (shapes.size() > MAX_CACHED_SHAPES) shapes.removeFirst();
        }
        return shapes.getAndMoveToLast(shape).advance(deadline);
    }

    public void clear() {
        shapes.clear();
    }

    private static final class Capture {

        private final DiscreteVoxelShape grid;
        private final DoubleList[] source;
        private final double[][] coordinates;
        private final VisionOccupancy runs = new VisionOccupancy();
        private final long cells;
        private int axis;
        private int coordinate;
        private long cell;
        private long start = -1;
        private @Nullable VisionShape result;

        private Capture(VoxelShape shape) {
            grid = ((PlatformVisionShapeAccessor) shape).dataEnergistics$visionGrid();
            source = new DoubleList[] { shape.getCoords(Direction.Axis.X), shape.getCoords(Direction.Axis.Y), shape.getCoords(Direction.Axis.Z) };
            cells = Math.multiplyExact(Math.multiplyExact((long) grid.getXSize(), grid.getYSize()), grid.getZSize());
            if (cells > MAX_CELLS || source[0].size() > MAX_COORDINATES || source[1].size() > MAX_COORDINATES || source[2].size() > MAX_COORDINATES) {
                coordinates = new double[][] { new double[] { 0 }, new double[] { 0 }, new double[] { 0 } };
                result = VisionShape.UNSUPPORTED;
            } else {
                coordinates = new double[][] { new double[source[0].size()], new double[source[1].size()], new double[source[2].size()] };
            }
        }

        private @Nullable VisionShape advance(long deadline) {
            if (result != null) return result;
            while (axis < 3) {
                if (System.nanoTime() >= deadline) return null;
                double value = source[axis].getDouble(coordinate);
                if (!Double.isFinite(value)) throw new IllegalArgumentException("Non-finite vision shape coordinate");
                coordinates[axis][coordinate++] = value;
                if (coordinate == coordinates[axis].length) {
                    coordinate = 0;
                    axis++;
                }
            }
            while (cell < cells) {
                if ((cell & 63) == 0 && System.nanoTime() >= deadline) return null;
                int z = (int) (cell % grid.getZSize());
                long row = cell / grid.getZSize();
                boolean filled = grid.isFull((int) (row / grid.getYSize()), (int) (row % grid.getYSize()), z);
                if (filled && start < 0) start = cell;
                else if (!filled && start >= 0) {
                    runs.append(start);
                    runs.append(cell);
                    start = -1;
                }
                cell++;
            }
            if (start >= 0) {
                runs.append(start);
                runs.append(cells);
            }
            if (runs.empty()) result = VisionShape.EMPTY;
            else if (runs.fills(cells)) result = new VisionShape(bounds(coordinates[0]), bounds(coordinates[1]), bounds(coordinates[2]), VisionShape.BLOCK.runs());
            else result = new VisionShape(coordinates[0], coordinates[1], coordinates[2], runs);
            return result;
        }

        private static double[] bounds(double[] coordinates) {
            return new double[] { coordinates[0], coordinates[coordinates.length - 1] };
        }
    }
}
