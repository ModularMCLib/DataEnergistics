package com.fish_dan_.data_energistics.common.block.plane;

import com.fish_dan_.data_energistics.common.blockentity.plane.AreaPlaneBlockEntity;
import com.fish_dan_.data_energistics.common.plane.model.AreaPlaneVariant;
import com.fish_dan_.data_energistics.common.plane.model.PlaneLayout;
import com.fish_dan_.data_energistics.common.plane.model.PlaneShapes;
import com.fish_dan_.data_energistics.common.plane.structure.AreaPlaneStructure;

import appeng.block.AEBaseEntityBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import org.jspecify.annotations.Nullable;

public final class AreaPlaneBlock extends AEBaseEntityBlock<AreaPlaneBlockEntity> {

    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    private final AreaPlaneVariant variant;

    public AreaPlaneBlock(BlockBehaviour.Properties properties, AreaPlaneVariant variant) {
        super(properties);
        this.variant = variant;
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    public AreaPlaneVariant variant() {
        return variant;
    }

    public PlaneLayout layout(BlockState state) {
        return new PlaneLayout(variant, state.getValue(FACING));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState().setValue(FACING, context.getNearestLookingDirection().getOpposite());
        return AreaPlaneStructure.canPlace(context, state) ? state : null;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moving) {
        if (!state.is(replacement.getBlock())) AreaPlaneStructure.removeParts(level, pos, layout(state));
        super.onRemove(state, level, pos, replacement, moving);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return rotate(state, mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return PlaneShapes.center(state.getValue(FACING));
    }
}
