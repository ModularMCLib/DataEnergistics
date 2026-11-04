package com.fish_dan_.data_energistics.common.plane.model;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.EnumMap;

public final class PlaneShapes {

    private static final EnumMap<Direction, VoxelShape> PLATES = new EnumMap<>(Direction.class);
    private static final EnumMap<Direction, VoxelShape> CENTERS = new EnumMap<>(Direction.class);

    static {
        for (Direction facing : Direction.values()) {
            VoxelShape plate = switch (facing) {
                case NORTH -> Block.box(0, 0, 0, 16, 16, 4);
                case SOUTH -> Block.box(0, 0, 12, 16, 16, 16);
                case WEST -> Block.box(0, 0, 0, 4, 16, 16);
                case EAST -> Block.box(12, 0, 0, 16, 16, 16);
                case DOWN -> Block.box(0, 0, 0, 16, 4, 16);
                case UP -> Block.box(0, 12, 0, 16, 16, 16);
            };
            VoxelShape port = switch (facing.getAxis()) {
                case X -> Block.box(0, 3, 3, 16, 13, 13);
                case Y -> Block.box(3, 0, 3, 13, 16, 13);
                case Z -> Block.box(3, 3, 0, 13, 13, 16);
            };
            PLATES.put(facing, plate);
            CENTERS.put(facing, Shapes.or(plate, port));
        }
    }

    private PlaneShapes() {}

    public static VoxelShape plate(Direction facing) {
        return PLATES.get(facing);
    }

    public static VoxelShape center(Direction facing) {
        return CENTERS.get(facing);
    }
}
