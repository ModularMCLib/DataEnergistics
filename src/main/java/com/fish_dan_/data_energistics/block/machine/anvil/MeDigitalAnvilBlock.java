package com.fish_dan_.data_energistics.block.machine.anvil;

import com.fish_dan_.data_energistics.blockentity.machine.anvil.MeDigitalAnvilBlockEntity;
import com.fish_dan_.data_energistics.menu.machine.anvil.MeDigitalAnvilMenu;

import appeng.block.AEBaseEntityBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import org.jspecify.annotations.NullMarked;

@NullMarked
public final class MeDigitalAnvilBlock extends AEBaseEntityBlock<MeDigitalAnvilBlockEntity> {

    public MeDigitalAnvilBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer serverPlayer && level.getBlockEntity(pos) instanceof MeDigitalAnvilBlockEntity host) {
            MeDigitalAnvilMenu.open(serverPlayer, host);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}
