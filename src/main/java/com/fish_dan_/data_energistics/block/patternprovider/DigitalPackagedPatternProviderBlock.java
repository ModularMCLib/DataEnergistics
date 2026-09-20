package com.fish_dan_.data_energistics.block.patternprovider;

import com.fish_dan_.data_energistics.blockentity.patternprovider.DigitalPackagedPatternProviderBlockEntity;
import com.fish_dan_.data_energistics.registry.DEBlockEntities;

import appeng.block.AEBaseEntityBlock;
import appeng.block.crafting.PatternProviderBlock;
import appeng.block.crafting.PushDirection;
import appeng.menu.locator.MenuLocators;
import appeng.util.InteractionUtil;
import appeng.util.Platform;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;

/** Retains AE2 directional placement and wrench controls without exposing remote-link controls. */
public final class DigitalPackagedPatternProviderBlock extends AEBaseEntityBlock<DigitalPackagedPatternProviderBlockEntity> {

    public DigitalPackagedPatternProviderBlock() {
        super(metalProps());
        registerDefaultState(defaultBlockState().setValue(PatternProviderBlock.PUSH_DIRECTION, PushDirection.ALL));
    }

    public void bindBlockEntity() {
        setBlockEntity(DigitalPackagedPatternProviderBlockEntity.class, DEBlockEntities.DIGITAL_PACKAGED_PATTERN_PROVIDER.get(), null, null);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos position, BlockState state) {
        return new DigitalPackagedPatternProviderBlockEntity(position, state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(PatternProviderBlock.PUSH_DIRECTION);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos position, Player player, BlockHitResult hit) {
        var provider = getBlockEntity(level, position);
        if (provider == null) return InteractionResult.PASS;
        if (!level.isClientSide()) provider.openMenu(player, MenuLocators.forBlockEntity(provider));
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos position,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (InteractionUtil.canWrenchRotate(stack)) {
            setSide(level, position, hit.getDirection());
            return ItemInteractionResult.sidedSuccess(level.isClientSide());
        }
        return super.useItemOn(stack, state, level, position, player, hand, hit);
    }

    /** Applies AE2's push-side cycle so the rendered arrow points toward the selected destination face. */
    public void setSide(Level level, BlockPos position, Direction face) {
        BlockState state = level.getBlockState(position);
        Direction current = state.getValue(PatternProviderBlock.PUSH_DIRECTION).getDirection();
        PushDirection selected;
        if (current == face.getOpposite()) {
            selected = PushDirection.fromDirection(face);
        } else if (current == face) {
            selected = PushDirection.ALL;
        } else if (current == null) {
            selected = PushDirection.fromDirection(face.getOpposite());
        } else {
            selected = PushDirection.fromDirection(Platform.rotateAround(current, face));
        }
        level.setBlockAndUpdate(position, state.setValue(PatternProviderBlock.PUSH_DIRECTION, selected));
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos position, Block block, BlockPos from, boolean moving) {
        var provider = getBlockEntity(level, position);
        if (provider != null) provider.getLogic().updateRedstoneState();
    }
}
