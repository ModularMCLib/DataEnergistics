package com.fish_dan_.data_energistics.common.plane.structure;

import com.fish_dan_.data_energistics.common.block.plane.AreaPlaneBlock;
import com.fish_dan_.data_energistics.common.block.plane.AreaPlaneSegmentBlock;
import com.fish_dan_.data_energistics.common.data.DEBlocks;
import com.fish_dan_.data_energistics.common.plane.model.PlaneLayout;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.function.BooleanSupplier;

/** World changes run on the server thread; unloaded footprint cells are never force-loaded. */
public final class AreaPlaneStructure {

    private AreaPlaneStructure() {}

    public static boolean canPlace(BlockPlaceContext context, BlockState state) {
        Level level = context.getLevel();
        PlaneLayout layout = ((AreaPlaneBlock) state.getBlock()).layout(state);
        var player = context.getPlayer();
        CollisionContext collision = player == null ? CollisionContext.empty() : CollisionContext.of(player);
        for (int slot = 0; slot < layout.variant().getSlotCount(); slot++) {
            BlockPos pos = layout.bodyPosition(context.getClickedPos(), slot);
            if (level.isOutsideBuildHeight(pos) || !level.getWorldBorder().isWithinBounds(pos) || !level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) return false;
            if (!level.getBlockState(pos).canBeReplaced(context) || level.getBlockEntity(pos) != null) return false;
            if (player != null && !level.mayInteract(player, pos)) return false;
            BlockState occupied = slot == layout.variant().getSlotCount() / 2 ? state : segmentState(layout, slot);
            if (!level.isUnobstructed(occupied, pos, collision)) return false;
        }
        return true;
    }

    public static boolean place(BlockPlaceContext context, BlockState state, BooleanSupplier placeCenter) {
        Level level = context.getLevel();
        BlockPos center = context.getClickedPos();
        PlaneLayout layout = ((AreaPlaneBlock) state.getBlock()).layout(state);
        var previous = new ObjectArrayList<BlockState>(layout.variant().getSlotCount());
        for (int slot = 0; slot < layout.variant().getSlotCount(); slot++) {
            previous.add(level.getBlockState(layout.bodyPosition(center, slot)));
        }
        if (!placeCenter.getAsBoolean()) return false;
        for (int slot = 0; slot < layout.variant().getSlotCount(); slot++) {
            if (slot == layout.variant().getSlotCount() / 2) continue;
            if (!level.setBlock(layout.bodyPosition(center, slot), segmentState(layout, slot), Block.UPDATE_ALL)) {
                rollback(level, center, state, layout, previous);
                return false;
            }
        }
        if (integrity(level, center, layout) != Integrity.COMPLETE) {
            rollback(level, center, state, layout, previous);
            return false;
        }
        return true;
    }

    private static void rollback(Level level, BlockPos center, BlockState state, PlaneLayout layout, ObjectArrayList<BlockState> previous) {
        if (level.getBlockState(center).is(state.getBlock())) {
            level.setBlock(center, previous.get(layout.variant().getSlotCount() / 2), Block.UPDATE_ALL);
        }
        for (int slot = 0; slot < layout.variant().getSlotCount(); slot++) {
            if (slot == layout.variant().getSlotCount() / 2) continue;
            BlockPos pos = layout.bodyPosition(center, slot);
            BlockState current = level.getBlockState(pos);
            if (current.isAir() || belongsTo(current, pos, center)) level.setBlock(pos, previous.get(slot), Block.UPDATE_ALL);
        }
    }

    public static void removeParts(Level level, BlockPos center, PlaneLayout layout) {
        for (int slot = 0; slot < layout.variant().getSlotCount(); slot++) {
            if (slot == layout.variant().getSlotCount() / 2) continue;
            BlockPos pos = layout.bodyPosition(center, slot);
            if (level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4) && belongsTo(level.getBlockState(pos), pos, center)) level.removeBlock(pos, false);
        }
    }

    public static Integrity integrity(Level level, BlockPos center, PlaneLayout layout) {
        boolean unloaded = false;
        for (int slot = 0; slot < layout.variant().getSlotCount(); slot++) {
            BlockPos pos = layout.bodyPosition(center, slot);
            if (!level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) {
                unloaded = true;
            } else if (slot == layout.variant().getSlotCount() / 2) {
                BlockState state = level.getBlockState(pos);
                if (!(state.getBlock() instanceof AreaPlaneBlock plane) || plane.getVariant() != layout.variant() ||
                        state.getValue(AreaPlaneBlock.FACING) != layout.facing())
                    return Integrity.BROKEN;
            } else if (!level.getBlockState(pos).equals(segmentState(layout, slot))) {
                return Integrity.BROKEN;
            }
        }
        return unloaded ? Integrity.UNLOADED : Integrity.COMPLETE;
    }

    public static boolean belongsTo(BlockState state, BlockPos pos, BlockPos center) {
        return state.is(DEBlocks.AREA_PLANE_SEGMENT.get()) && AreaPlaneSegmentBlock.center(pos, state).equals(center);
    }

    private static BlockState segmentState(PlaneLayout layout, int slot) {
        return DEBlocks.AREA_PLANE_SEGMENT.get().defaultBlockState()
                .setValue(AreaPlaneBlock.FACING, layout.facing())
                .setValue(AreaPlaneSegmentBlock.SEGMENT, layout.segmentIndex(slot));
    }

    public enum Integrity {
        COMPLETE,
        UNLOADED,
        BROKEN
    }
}
