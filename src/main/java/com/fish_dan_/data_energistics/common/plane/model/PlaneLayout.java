package com.fish_dan_.data_energistics.common.plane.model;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/** Each depth layer is row-major when viewed from the working face; depth starts in front of the plate. */
public record PlaneLayout(AreaPlaneVariant variant, Direction facing) {

    public Direction up() {
        return switch (facing) {
            case UP -> Direction.NORTH;
            case DOWN -> Direction.SOUTH;
            default -> Direction.UP;
        };
    }

    public Direction right() {
        return facing.getAxis().isVertical() ? Direction.EAST : facing.getCounterClockWise();
    }

    public BlockPos bodyPosition(BlockPos center, int slot) {
        int radius = variant.getWidth() / 2;
        return center.relative(right(), slot % variant.getWidth() - radius)
                .relative(up(), radius - slot / variant.getWidth());
    }

    public BlockPos targetPosition(BlockPos center, int slot) {
        int faceCells = variant.getFaceCellCount();
        return bodyPosition(center, slot % faceCells).relative(facing, 1 + slot / faceCells);
    }

    public int segmentIndex(int slot) {
        int inset = 2 - variant.getWidth() / 2;
        return (slot / variant.getWidth() + inset) * 5 + slot % variant.getWidth() + inset;
    }

    public boolean containsSegment(int segment) {
        int radius = variant.getWidth() / 2;
        return Math.abs(segment % 5 - 2) <= radius && Math.abs(segment / 5 - 2) <= radius;
    }

    public static BlockPos centerFromSegment(BlockPos position, Direction facing, int segment) {
        PlaneLayout layout = new PlaneLayout(AreaPlaneVariant.EXTREMELY_LARGE_FORMATION, facing);
        return position.relative(layout.right(), 2 - segment % 5).relative(layout.up(), segment / 5 - 2);
    }
}
