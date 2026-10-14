package com.fish_dan_.data_energistics.block.research;

import com.fish_dan_.data_energistics.blockentity.research.ResearchStationBlockEntity;
import com.fish_dan_.data_energistics.menu.research.ResearchStationMenu;
import com.fish_dan_.data_energistics.registry.DEBlocks;

import appeng.block.AEBaseEntityBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
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

import org.jspecify.annotations.Nullable;

/** Main, right-hand anchor; the housing extends one block to its local left. */
public final class ResearchStationBlock extends AEBaseEntityBlock<ResearchStationBlockEntity> {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    public ResearchStationBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH).setValue(ACTIVE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ACTIVE);
    }

    public static BlockPos partner(BlockPos pos, BlockState state) {
        return pos.relative(state.getValue(FACING).getCounterClockWise());
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
        BlockPos other = partner(context.getClickedPos(), state);
        return context.getLevel().getBlockState(other).canBeReplaced(context) && context.getLevel().getWorldBorder().isWithinBounds(other) ? state : null;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide()) {
            BlockPos other = partner(pos, state);
            BlockState part = DEBlocks.RESEARCH_STATION_PART.get().defaultBlockState().setValue(FACING, state.getValue(FACING));
            if (level.setBlock(other, part, Block.UPDATE_ALL)) {
                DEBlocks.RESEARCH_STATION_PART.get().setPlacedBy(level, other, part, placer, stack);
            }
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moving) {
        if (!state.is(replacement.getBlock())) {
            BlockPos other = partner(pos, state);
            if (level.getBlockState(other).is(DEBlocks.RESEARCH_STATION_PART.get())) level.removeBlock(other, false);
        }
        super.onRemove(state, level, pos, replacement, moving);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer serverPlayer && level.getBlockEntity(pos) instanceof ResearchStationBlockEntity host) {
            ResearchStationMenu.open(serverPlayer, host);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
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
    protected BlockState updateBlockStateFromBlockEntity(BlockState state, ResearchStationBlockEntity host) {
        return state.setValue(ACTIVE, host.working());
    }
}
