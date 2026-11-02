package com.fish_dan_.data_energistics.common.block.plane;

import com.fish_dan_.data_energistics.common.plane.model.PlaneLayout;
import com.fish_dan_.data_energistics.common.plane.model.PlaneShapes;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import org.jspecify.annotations.Nullable;

/** A saved local offset replaces per-cell block entities and avoids additional network nodes. */
public final class AreaPlaneSegmentBlock extends Block {

    public static final IntegerProperty SEGMENT = IntegerProperty.create("segment", 0, 24);

    public AreaPlaneSegmentBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(AreaPlaneBlock.FACING, Direction.NORTH).setValue(SEGMENT, 0));
    }

    public static BlockPos center(BlockPos pos, BlockState state) {
        return PlaneLayout.centerFromSegment(pos, state.getValue(AreaPlaneBlock.FACING), state.getValue(SEGMENT));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AreaPlaneBlock.FACING, SEGMENT);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return PlaneShapes.plate(state.getValue(AreaPlaneBlock.FACING));
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moving) {
        if (!level.isClientSide()) level.scheduleTick(pos, this, 20);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        BlockPos center = center(pos, state);
        if (level.getChunkSource().hasChunk(center.getX() >> 4, center.getZ() >> 4)) {
            BlockState main = level.getBlockState(center);
            if (!(main.getBlock() instanceof AreaPlaneBlock plane) ||
                    main.getValue(AreaPlaneBlock.FACING) != state.getValue(AreaPlaneBlock.FACING) ||
                    !plane.layout(main).containsSegment(state.getValue(SEGMENT))) {
                level.removeBlock(pos, false);
                return;
            }
        }
        // Scheduled ticks survive chunk unloads, so orphaned cells clean themselves when reloaded.
        level.scheduleTick(pos, this, 20);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        destroyCenter(level, pos, state, !player.isCreative(), player);
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moving) {
        if (!state.is(replacement.getBlock())) destroyCenter(level, pos, state, true, null);
        super.onRemove(state, level, pos, replacement, moving);
    }

    private static void destroyCenter(Level level, BlockPos pos, BlockState state, boolean drop, @Nullable Player player) {
        BlockPos center = center(pos, state);
        if (!level.isClientSide() && level.getChunkSource().hasChunk(center.getX() >> 4, center.getZ() >> 4) && level.getBlockState(center).getBlock() instanceof AreaPlaneBlock) {
            level.destroyBlock(center, drop, player);
        }
    }
}
