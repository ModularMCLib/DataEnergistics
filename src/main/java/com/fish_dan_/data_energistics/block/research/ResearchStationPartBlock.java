package com.fish_dan_.data_energistics.block.research;

import com.fish_dan_.data_energistics.blockentity.research.ResearchStationBlockEntity;
import com.fish_dan_.data_energistics.menu.research.ResearchStationMenu;
import com.fish_dan_.data_energistics.registry.DEBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;

/** No independent inventory or loot; breaking the left housing destroys the authoritative main block. */
public final class ResearchStationPartBlock extends Block {

    public ResearchStationPartBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ResearchStationBlock.FACING);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    private static BlockPos main(BlockPos pos, BlockState state) {
        return pos.relative(state.getValue(ResearchStationBlock.FACING).getCounterClockWise());
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moving) {
        if (!state.is(replacement.getBlock())) {
            BlockPos main = main(pos, state);
            if (level.getBlockState(main).is(DEBlocks.RESEARCH_STATION.get())) level.destroyBlock(main, true);
        }
        super.onRemove(state, level, pos, replacement, moving);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer serverPlayer && level.getBlockEntity(main(pos, state)) instanceof ResearchStationBlockEntity host) {
            ResearchStationMenu.open(serverPlayer, host);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide()) {
            BlockPos main = main(pos, state);
            if (level.getBlockState(main).is(DEBlocks.RESEARCH_STATION.get())) level.destroyBlock(main, !player.isCreative(), player);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }
}
