package com.fish_dan_.data_energistics.item.connector;

import com.fish_dan_.data_energistics.ae2.patternprovider.adaptive.AdaptivePatternProviderLogic;
import com.fish_dan_.data_energistics.ae2.patternprovider.adaptive.AdaptivePatternProviderResolver;
import com.fish_dan_.data_energistics.block.tower.DataDistributionTowerBlock;
import com.fish_dan_.data_energistics.blockentity.patternprovider.AdaptivePatternProviderBlockEntity;
import com.fish_dan_.data_energistics.blockentity.tower.DataDistributionTowerBlockEntity;
import com.fish_dan_.data_energistics.part.AdaptivePatternProviderPart;
import com.fish_dan_.data_energistics.registry.DEBlocks;
import com.fish_dan_.data_energistics.registry.DEDataComponents;

import appeng.api.parts.IPart;
import appeng.api.AECapabilities;
import appeng.api.behaviors.GenericInternalInventory;
import appeng.blockentity.networking.CableBusBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import org.jspecify.annotations.Nullable;

import java.util.List;

public class DataDistributionConnectorItem extends Item {

    private static final String KEY_PREFIX = "item.data_energistics.data_distribution_connector";

    public DataDistributionConnectorItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }

        Level level = context.getLevel();
        BlockPos clickedPos = context.getClickedPos();
        BlockState clickedState = level.getBlockState(clickedPos);
        ItemStack stack = context.getItemInHand();

        if (player.isShiftKeyDown() && isAdaptiveProvider(level, clickedPos, context.getClickedFace())) {
            return bindAdaptiveProvider(stack, player, level, clickedPos, context.getClickedFace());
        }
        if (clickedState.is(DEBlocks.DATA_DISTRIBUTION_TOWER.get()) && player.isShiftKeyDown()) {
            return bindTower(stack, player, level, clickedPos, clickedState);
        }

        if (!getConnectorData(stack).hasSelection()) {
            return InteractionResult.PASS;
        }

        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        return connectTarget(stack, player, level, clickedPos, context.getClickedFace(), true);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltipComponents,
                                TooltipFlag tooltipFlag) {
        DataDistributionConnectorItemData data = getConnectorData(stack);
        if (!data.hasSelection()) {
            return;
        }

        if (data.isAdaptiveProvider()) {
            tooltipComponents.add(Component.translatable(
                    KEY_PREFIX + ".tooltip.bound_provider",
                    data.providerDimensionId(),
                    data.getProviderPos().getX(),
                    data.getProviderPos().getY(),
                    data.getProviderPos().getZ()));
            return;
        }

        BlockPos pos = data.getTowerPos();
        tooltipComponents.add(Component.translatable(
                KEY_PREFIX + ".tooltip.bound",
                data.dimensionId(),
                pos.getX(),
                pos.getY(),
                pos.getZ()));
    }

    /**
     * Binds the supplied connector stack to the clicked distribution tower for both held and equipped workflows.
     * Client calls consume the interaction immediately, while the server validates point mode and persists the
     * selected tower into the original mutable stack.
     *
     * @param stack        original connector stack that receives the tower selection component
     * @param player       player selecting the tower and receiving success or failure feedback
     * @param level        level containing the clicked tower
     * @param clickedPos   position of any clicked tower part
     * @param clickedState state of the clicked tower part used to resolve its base position
     * @return {@link InteractionResult#SUCCESS} when the selection is accepted, {@link InteractionResult#FAIL} for a
     *         tower outside point-to-point mode, or {@link InteractionResult#PASS} when the base block entity is
     *         unavailable
     */
    public InteractionResult bindTower(ItemStack stack, Player player, Level level, BlockPos clickedPos,
                                       BlockState clickedState) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        BlockPos basePos = DataDistributionTowerBlock.getBasePos(clickedPos, clickedState);
        BlockEntity blockEntity = level.getBlockEntity(basePos);
        if (!(blockEntity instanceof DataDistributionTowerBlockEntity tower)) {
            return InteractionResult.PASS;
        }
        if (!tower.isPointToPointMode()) {
            player.displayClientMessage(Component.translatable(KEY_PREFIX + ".point_mode_only"), true);
            return InteractionResult.FAIL;
        }

        stack.set(DEDataComponents.DATA_DISTRIBUTION_CONNECTOR.get(),
                getConnectorData(stack).withTower(level.dimension().location().toString(), basePos));
        player.displayClientMessage(Component.translatable(
                KEY_PREFIX + ".bound",
                basePos.getX(),
                basePos.getY(),
                basePos.getZ()), true);
        return InteractionResult.SUCCESS;
    }

    public void autoConnectPlacedBlock(ItemStack stack, Player player, Level level, BlockPos placedPos) {
        if (level.isClientSide() || !getConnectorData(stack).hasSelection()) {
            return;
        }
        connectTarget(stack, player, level, placedPos, Direction.UP, false);
    }

    private InteractionResult connectTarget(ItemStack stack, Player player, Level level, BlockPos clickedPos,
                                            Direction clickedFace, boolean showFailureMessages) {
        DataDistributionConnectorItemData data = getConnectorData(stack);
        if (!data.hasSelection()) {
            if (showFailureMessages) {
                player.displayClientMessage(Component.translatable(KEY_PREFIX + ".unbound"), true);
            }
            return InteractionResult.FAIL;
        }

        if (data.isAdaptiveProvider()) {
            return connectAdaptiveProvider(stack, player, level, clickedPos, clickedFace, showFailureMessages);
        }

        if (!level.dimension().location().toString().equals(data.dimensionId())) {
            if (showFailureMessages) {
                player.displayClientMessage(Component.translatable(KEY_PREFIX + ".tower_missing"), true);
            }
            return InteractionResult.FAIL;
        }

        ResourceKey<Level> dimensionKey = ResourceKey.create(Registries.DIMENSION,
                ResourceLocation.parse(data.dimensionId()));
        if (!level.dimension().equals(dimensionKey)) {
            if (showFailureMessages) {
                player.displayClientMessage(Component.translatable(KEY_PREFIX + ".tower_missing"), true);
            }
            return InteractionResult.FAIL;
        }

        BlockPos towerPos = data.getTowerPos();
        if (!level.isLoaded(towerPos)) {
            if (showFailureMessages) {
                player.displayClientMessage(Component.translatable(KEY_PREFIX + ".tower_missing"), true);
            }
            return InteractionResult.FAIL;
        }

        BlockEntity blockEntity = level.getBlockEntity(towerPos);
        if (!(blockEntity instanceof DataDistributionTowerBlockEntity tower)) {
            stack.set(DEDataComponents.DATA_DISTRIBUTION_CONNECTOR.get(), data.clear());
            if (showFailureMessages) {
                player.displayClientMessage(Component.translatable(KEY_PREFIX + ".tower_missing"), true);
            }
            return InteractionResult.FAIL;
        }
        if (!tower.isPointToPointMode()) {
            if (showFailureMessages) {
                player.displayClientMessage(Component.translatable(KEY_PREFIX + ".point_mode_only"), true);
            }
            return InteractionResult.FAIL;
        }

        DataDistributionTowerBlockEntity.ConnectorBindResult result = tower.bindTargetFromConnector(clickedPos);
        if (!result.success()) {
            if (showFailureMessages) {
                player.displayClientMessage(Component.translatable(switch (result.failure()) {
                    case NOT_POINT_MODE -> KEY_PREFIX + ".point_mode_only";
                    case OUT_OF_RANGE -> KEY_PREFIX + ".target_out_of_range";
                    case SELF_TARGET -> KEY_PREFIX + ".target_self";
                    case UNSUPPORTED -> KEY_PREFIX + ".target_invalid";
                }), true);
            }
            return InteractionResult.FAIL;
        }

        String suffix = result.aeSupported() && result.feSupported() ? ".connected.af" : result.aeSupported() ? ".connected.ae" : ".connected.fe";
        player.displayClientMessage(Component.translatable(
                KEY_PREFIX + suffix,
                clickedPos.getX(),
                clickedPos.getY(),
                clickedPos.getZ()), true);
        return InteractionResult.SUCCESS;
    }

    private InteractionResult bindAdaptiveProvider(ItemStack stack, Player player, Level level,
                                                   BlockPos position, Direction clickedFace) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        int side = -1;
        BlockEntity blockEntity = level.getBlockEntity(position);
        if (blockEntity instanceof CableBusBlockEntity cableBus) {
            Direction exposedSide = clickedFace.getOpposite();
            IPart part = cableBus.getPart(exposedSide);
            if (!(part instanceof AdaptivePatternProviderPart)) {
                return InteractionResult.FAIL;
            }
            side = exposedSide.get3DDataValue();
        } else if (!(blockEntity instanceof AdaptivePatternProviderBlockEntity)) {
            return InteractionResult.FAIL;
        }
        stack.set(DEDataComponents.DATA_DISTRIBUTION_CONNECTOR.get(),
                getConnectorData(stack).withAdaptiveProvider(
                        level.dimension().location().toString(), position, side));
        player.displayClientMessage(Component.translatable(KEY_PREFIX + ".bound_provider"), true);
        return InteractionResult.SUCCESS;
    }

    private InteractionResult connectAdaptiveProvider(ItemStack stack, Player player, Level level,
                                                      BlockPos clickedPos, Direction clickedFace,
                                                      boolean showFailureMessages) {
        DataDistributionConnectorItemData data = getConnectorData(stack);
        if (!level.dimension().location().toString().equals(data.providerDimensionId()) || !level.isLoaded(data.getProviderPos())) {
            if (showFailureMessages) {
                player.displayClientMessage(Component.translatable(KEY_PREFIX + ".provider_missing"), true);
            }
            return InteractionResult.FAIL;
        }
        BlockEntity blockEntity = level.getBlockEntity(data.getProviderPos());
        boolean valid = blockEntity instanceof AdaptivePatternProviderBlockEntity;
        if (blockEntity instanceof CableBusBlockEntity cableBus && data.providerSide() >= 0) {
            Direction side = Direction.from3DDataValue(data.providerSide());
            valid = cableBus.getPart(side) instanceof AdaptivePatternProviderPart;
        }
        if (!valid || !AdaptivePatternProviderResolver.isSupportedProviderStack(
                providerStack(blockEntity, data.providerSide()))) {
            if (showFailureMessages) {
                player.displayClientMessage(Component.translatable(KEY_PREFIX + ".provider_invalid"), true);
            }
            return InteractionResult.FAIL;
        }
        if (!hasTargetCapability(level, clickedPos, clickedFace.getOpposite())) {
            if (showFailureMessages) {
                player.displayClientMessage(Component.translatable(KEY_PREFIX + ".target_invalid"), true);
            }
            return InteractionResult.FAIL;
        }
        AdaptivePatternProviderLogic logic = adaptiveLogic(blockEntity, data.providerSide());
        if (logic == null || !logic.bindConnectorTarget(clickedPos, clickedFace.getOpposite())) {
            if (showFailureMessages) {
                player.displayClientMessage(Component.translatable(KEY_PREFIX + ".target_invalid"), true);
            }
            return InteractionResult.FAIL;
        }
        return InteractionResult.SUCCESS;
    }

    private static boolean hasTargetCapability(Level level, BlockPos position, Direction side) {
        BlockState state = level.getBlockState(position);
        BlockEntity blockEntity = level.getBlockEntity(position);
        GenericInternalInventory generic = level.getCapability(
                AECapabilities.GENERIC_INTERNAL_INV, position, state, blockEntity, side);
        if (generic != null) {
            return true;
        }
        IItemHandler items = level.getCapability(Capabilities.ItemHandler.BLOCK, position, state, blockEntity, side);
        if (items != null) {
            return true;
        }
        IFluidHandler fluids = level.getCapability(Capabilities.FluidHandler.BLOCK, position, state, blockEntity, side);
        return fluids != null;
    }


    private static boolean isAdaptiveProvider(Level level, BlockPos position, Direction clickedFace) {
        BlockEntity blockEntity = level.getBlockEntity(position);
        if (blockEntity instanceof AdaptivePatternProviderBlockEntity) {
            return true;
        }
        return blockEntity instanceof CableBusBlockEntity cableBus && cableBus.getPart(clickedFace.getOpposite()) instanceof AdaptivePatternProviderPart;
    }

    private static ItemStack providerStack(BlockEntity blockEntity, int side) {
        if (blockEntity instanceof AdaptivePatternProviderBlockEntity provider) {
            return provider.getProviderStack();
        }
        if (blockEntity instanceof CableBusBlockEntity cableBus && side >= 0 && cableBus.getPart(Direction.from3DDataValue(side)) instanceof AdaptivePatternProviderPart part) {
            return part.getProviderStack();
        }
        return ItemStack.EMPTY;
    }

    @Nullable
    private static AdaptivePatternProviderLogic adaptiveLogic(BlockEntity blockEntity, int side) {
        if (blockEntity instanceof AdaptivePatternProviderBlockEntity provider && provider.getLogic() instanceof AdaptivePatternProviderLogic logic) {
            return logic;
        }
        if (blockEntity instanceof CableBusBlockEntity cableBus && side >= 0 && cableBus.getPart(Direction.from3DDataValue(side)) instanceof AdaptivePatternProviderPart part) {
            return part.getLogic();
        }
        return null;
    }

    private static DataDistributionConnectorItemData getConnectorData(ItemStack stack) {
        DataDistributionConnectorItemData data = stack.get(DEDataComponents.DATA_DISTRIBUTION_CONNECTOR.get());
        return data != null ? data : DataDistributionConnectorItemData.EMPTY;
    }
}
