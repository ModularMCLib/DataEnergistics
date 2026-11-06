package com.fish_dan_.data_energistics.util.client.vision;

import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.doubles.DoubleComparators;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import lombok.RequiredArgsConstructor;

import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Worker-only union contours. Neighbor queries use captured occupancy, never live world state. */
@RequiredArgsConstructor
@OnlyIn(Dist.CLIENT)
public final class SonarSurfaceContours {

    private static final double EPSILON = 1.0e-5;
    private final VisionSectionSnapshot snapshot;
    private final BooleanSupplier cancelled;
    private final ObjectOpenHashSet<Edge> edges = new ObjectOpenHashSet<>();
    private final DoubleArrayList cuts = new DoubleArrayList();

    public void add(int bx, int by, int bz, float x1, float y1, float z1, float x2, float y2, float z2) {
        checkCancelled();
        Direction.Axis axis = x1 != x2 ? Direction.Axis.X : y1 != y2 ? Direction.Axis.Y : Direction.Axis.Z;
        double a = axis.choose(x1, y1, z1), b = axis.choose(x2, y2, z2);
        double start = Math.min(a, b), end = Math.max(a, b);
        double u = axis == Direction.Axis.X ? y1 : x1;
        double v = axis == Direction.Axis.Z ? y1 : z1;
        cuts.clear();
        cuts.add(start);
        cuts.add(end);
        for (int cell = Mth.floor(start); cell <= Mth.floor(end - EPSILON); cell++) {
            checkCancelled();
            if (cell > start) cuts.add(cell);
            double along = (Math.max(start, cell) + Math.min(end, cell + 1)) * 0.5;
            for (int side = 0; side < 4; side++) {
                double across = u + ((side & 1) == 0 ? -EPSILON : EPSILON), vertical = v + ((side & 2) == 0 ? -EPSILON : EPSILON);
                int nx = Mth.floor(axis == Direction.Axis.X ? along : across);
                int ny = Mth.floor(axis == Direction.Axis.Y ? along : axis == Direction.Axis.X ? across : vertical);
                int nz = Mth.floor(axis == Direction.Axis.Z ? along : vertical);
                for (double coordinate : snapshot.shape(nx, ny, nz).coordinates(axis)) {
                    checkCancelled();
                    double cut = axis.choose(nx, ny, nz) + coordinate;
                    if (cut > start && cut < end) cuts.add(cut);
                }
            }
        }
        cuts.sort(DoubleComparators.NATURAL_COMPARATOR);
        for (int index = 1; index < cuts.size(); index++) {
            checkCancelled();
            double from = cuts.getDouble(index - 1), to = cuts.getDouble(index);
            if (to - from < EPSILON) continue;
            int filled = 0;
            for (int side = 0; side < 4; side++) {
                double across = u + ((side & 1) == 0 ? -EPSILON : EPSILON), vertical = v + ((side & 2) == 0 ? -EPSILON : EPSILON);
                if (occupied(bx, by, bz, axis, (from + to) * 0.5, across, vertical)) filled |= 1 << side;
            }
            if (filled == 0 || filled == 15 || filled == 3 || filled == 5 || filled == 10 || filled == 12) continue;
            edges.add(switch (axis) {
                case X -> new Edge((float) from, (float) u, (float) v, (float) to, (float) u, (float) v);
                case Y -> new Edge((float) u, (float) from, (float) v, (float) u, (float) to, (float) v);
                case Z -> new Edge((float) u, (float) v, (float) from, (float) u, (float) v, (float) to);
            });
        }
    }

    private boolean occupied(int bx, int by, int bz, Direction.Axis axis, double along, double u, double v) {
        double x = axis == Direction.Axis.X ? along : u;
        double y = axis == Direction.Axis.Y ? along : axis == Direction.Axis.X ? u : v;
        double z = axis == Direction.Axis.Z ? along : v;
        VisionShape source = snapshot.shape(bx, by, bz);
        if (source.contains(x - bx, y - by, z - bz)) return true;
        int nx = Mth.floor(x), ny = Mth.floor(y), nz = Mth.floor(z);
        return snapshot.shape(nx, ny, nz).contains(x - nx, y - ny, z - nz);
    }

    private void checkCancelled() {
        if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted()) throw new CancellationException();
    }

    public void write(Consumer<Edge> output) {
        edges.forEach(output);
    }

    public record Edge(float x1, float y1, float z1, float x2, float y2, float z2) {}
}
