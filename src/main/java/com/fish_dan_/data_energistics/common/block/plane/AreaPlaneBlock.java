package com.fish_dan_.data_energistics.common.block.plane;

import com.fish_dan_.data_energistics.common.blockentity.plane.AreaPlaneBlockEntity;
import com.fish_dan_.data_energistics.common.gui.plane.AreaPlaneUi;
import com.fish_dan_.data_energistics.common.plane.model.AreaPlaneVariant;
import com.fish_dan_.data_energistics.common.plane.model.PlaneLayout;
import com.fish_dan_.data_energistics.common.plane.model.PlaneShapes;
import com.fish_dan_.data_energistics.common.plane.structure.AreaPlaneStructure;

import com.lowdragmc.lowdraglib2.gui.factory.BlockUIMenuType;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;

import appeng.block.AEBaseEntityBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import lombok.Getter;
import org.jspecify.annotations.Nullable;

public final class AreaPlaneBlock extends AEBaseEntityBlock<AreaPlaneBlockEntity> implements BlockUIMenuType.BlockUI {

    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
    @Getter
    private final AreaPlaneVariant variant;

    public AreaPlaneBlock(BlockBehaviour.Properties properties, AreaPlaneVariant variant) {
        super(properties);
        this.variant = variant;
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH).setValue(ACTIVE, false));
    }

    public PlaneLayout layout(BlockState state) {
        return new PlaneLayout(variant, state.getValue(FACING));
    }

    @Override
    public ModularUI createUI(BlockUIMenuType.BlockUIHolder holder) {
        if (!(holder.player.level().getBlockEntity(holder.pos) instanceof AreaPlaneBlockEntity host)) {
            throw new IllegalStateException("Area plane UI has no block entity at " + holder.pos);
        }
        return AreaPlaneUi.create(host, holder.player);
    }

    @Override
    public boolean stillValid(BlockUIMenuType.BlockUIHolder holder) {
        return BlockUIMenuType.BlockUI.super.stillValid(holder) &&
                holder.player.level().getBlockEntity(holder.pos) instanceof AreaPlaneBlockEntity &&
                holder.player.distanceToSqr(holder.pos.getX() + 0.5, holder.pos.getY() + 0.5, holder.pos.getZ() + 0.5) <= 64;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer serverPlayer) BlockUIMenuType.openUI(serverPlayer, pos);
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ACTIVE);
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
