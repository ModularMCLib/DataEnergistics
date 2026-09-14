package com.fish_dan_.data_energistics.block.ioport;

import com.fish_dan_.data_energistics.blockentity.ioport.DataIoPortBlockEntity;
import com.fish_dan_.data_energistics.registry.DEMenus;

import appeng.api.orientation.IOrientationStrategy;
import appeng.api.orientation.OrientationStrategies;
import appeng.block.AEBaseEntityBlock;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocators;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;

/** Directional storage-cell I/O port block. */
public final class DataIoPortBlock extends AEBaseEntityBlock<DataIoPortBlockEntity> {

    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    public DataIoPortBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH).setValue(POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(POWERED);
    }

    @Override
    public IOrientationStrategy getOrientationStrategy() {
        return OrientationStrategies.full();
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor,
                                BlockPos neighborPos, boolean moving) {
        super.neighborChanged(state, level, pos, neighbor, neighborPos, moving);
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof DataIoPortBlockEntity port) {
            port.updateRedstoneState();
        }
    }

    @Override
    protected BlockState updateBlockStateFromBlockEntity(BlockState state, DataIoPortBlockEntity entity) {
        return state.setValue(POWERED, entity.isActive());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hitResult) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof DataIoPortBlockEntity port) {
            MenuOpener.open(DEMenus.DATA_IO_PORT.get(), player, MenuLocators.forBlockEntity(port));
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}
