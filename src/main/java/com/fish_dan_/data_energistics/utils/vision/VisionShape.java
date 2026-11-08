package com.fish_dan_.data_energistics.utils.vision;

import net.minecraft.core.Direction;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.Arrays;

/** Owned coordinates and occupied runs, safely published after capture and never mutated by workers. */
@OnlyIn(Dist.CLIENT)
public record VisionShape(double[] x, double[] y, double[] z, VisionOccupancy runs) {

    public static final VisionShape EMPTY = new VisionShape(new double[] { 0 }, new double[] { 0 }, new double[] { 0 }, new VisionOccupancy());
    public static final VisionShape UNSUPPORTED = new VisionShape(new double[] { 0 }, new double[] { 0 }, new double[] { 0 }, new VisionOccupancy());
    public static final VisionShape BLOCK = box(1);

    public static VisionShape box(double height) {
        var runs = new VisionOccupancy();
        runs.append(0);
        runs.append(1);
        return new VisionShape(new double[] { 0, 1 }, new double[] { 0, height }, new double[] { 0, 1 }, runs);
    }

    public double[] coordinates(Direction.Axis axis) {
        return switch (axis) {
            case X -> x;
            case Y -> y;
            case Z -> z;
        };
    }

    public boolean empty() {
        return runs.empty();
    }

    public boolean filled(int cx, int cy, int cz) {
        if (cx < 0 || cy < 0 || cz < 0 || cx >= x.length - 1 || cy >= y.length - 1 || cz >= z.length - 1) return false;
        long cell = ((long) cx * (y.length - 1) + cy) * (z.length - 1) + cz;
        return runs.contains(cell);
    }

    public boolean contains(double px, double py, double pz) {
        return filled(cell(x, px), cell(y, py), cell(z, pz));
    }

    private static int cell(double[] coordinates, double position) {
        int index = Arrays.binarySearch(coordinates, position);
        return index >= 0 ? index : -index - 2;
    }
}
