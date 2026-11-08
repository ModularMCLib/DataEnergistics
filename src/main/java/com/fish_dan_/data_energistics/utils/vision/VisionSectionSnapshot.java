package com.fish_dan_.data_energistics.utils.vision;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/** A section plus one block of neighboring shapes; contains no level, chunk, state or live shape references. */
@OnlyIn(Dist.CLIENT)
public record VisionSectionSnapshot(long origin, int modes, VisionShape[] shapes, int[] temperatures, int[] visibleFaces) {

    public static final int THERMAL = 1;
    public static final int SONAR = 2;

    public VisionShape shape(int x, int y, int z) {
        return x < -1 || y < -1 || z < -1 || x > 16 || y > 16 || z > 16 ? VisionShape.EMPTY : shapes[index(x, y, z)];
    }

    public static int index(int x, int y, int z) {
        return ((y + 1) * 18 + z + 1) * 18 + x + 1;
    }
}
