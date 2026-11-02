package com.fish_dan_.data_energistics.common.block.plane;

import com.fish_dan_.data_energistics.common.plane.model.AreaPlaneVariant;
import com.fish_dan_.data_energistics.common.plane.model.PlaneLayout;
import com.fish_dan_.data_energistics.common.plane.model.PlaneShapes;

import com.lowdragmc.lowdraglib2.gui.factory.BlockUIMenuType;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import org.jspecify.annotations.Nullable;

import java.util.function.UnaryOperator;

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
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        BlockPos center = center(pos, state);
        if (player instanceof ServerPlayer serverPlayer && level.getChunkSource().hasChunk(center.getX() >> 4, center.getZ() >> 4) &&
                level.getBlockState(center).getBlock() instanceof AreaPlaneBlock)
            BlockUIMenuType.openUI(serverPlayer, center);
        return InteractionResult.sidedSuccess(level.isClientSide());
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
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return transform(state, rotation::rotate);
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return transform(state, mirror::mirror);
    }

    private static BlockState transform(BlockState state, UnaryOperator<Direction> transform) {
        Direction facing = state.getValue(AreaPlaneBlock.FACING);
        int segment = state.getValue(SEGMENT);
        PlaneLayout oldLayout = new PlaneLayout(AreaPlaneVariant.EXTREMELY_LARGE_FORMATION, facing);
        PlaneLayout nextLayout = new PlaneLayout(oldLayout.variant(), transform.apply(facing));
        BlockPos offset = BlockPos.ZERO.relative(transform.apply(oldLayout.right()), segment % 5 - 2)
                .relative(transform.apply(oldLayout.up()), 2 - segment / 5);
        int column = offset.getX() * nextLayout.right().getStepX() + offset.getY() * nextLayout.right().getStepY() + offset.getZ() * nextLayout.right().getStepZ();
        int row = offset.getX() * nextLayout.up().getStepX() + offset.getY() * nextLayout.up().getStepY() + offset.getZ() * nextLayout.up().getStepZ();
        return state.setValue(AreaPlaneBlock.FACING, nextLayout.facing()).setValue(SEGMENT, (2 - row) * 5 + column + 2);
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
