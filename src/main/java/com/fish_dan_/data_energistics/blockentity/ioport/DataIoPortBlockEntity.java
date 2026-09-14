package com.fish_dan_.data_energistics.blockentity.ioport;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.ae2.ioport.DataIoPortTransfer;
import com.fish_dan_.data_energistics.registry.DEBlockEntities;
import com.fish_dan_.data_energistics.registry.DEBlocks;
import com.fish_dan_.data_energistics.registry.DEItems;

import appeng.api.config.FullnessMode;
import appeng.api.config.OperationMode;
import appeng.api.config.RedstoneMode;
import appeng.api.config.Settings;
import appeng.api.inventories.ISegmentedInventory;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.storage.StorageCells;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.api.upgrades.UpgradeInventories;
import appeng.api.util.AECableType;
import appeng.api.util.IConfigManager;
import appeng.api.util.IConfigurableObject;
import appeng.blockentity.grid.AENetworkedInvBlockEntity;
import appeng.core.definitions.AEItems;
import appeng.core.settings.TickRates;
import appeng.me.helpers.MachineSource;
import appeng.util.SettingsFrom;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.CombinedInternalInventory;
import appeng.util.inv.FilteredInternalInventory;
import appeng.util.inv.filter.AEItemFilters;
import appeng.util.inv.filter.IAEItemFilter;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;

import org.jspecify.annotations.Nullable;

import java.util.List;

/** AE2 storage-cell I/O port with long-range transfer quotas and parallel transfer rounds. */
public final class DataIoPortBlockEntity extends AENetworkedInvBlockEntity
                                         implements IGridTickable, IUpgradeableObject, IConfigurableObject {

    public static final int CELL_SLOTS = 6;
    public static final int UPGRADE_SLOTS = 8;
    private static final String ITEM_STATE = "data_io_port";

    private final AppEngInternalInventory inputCells = new AppEngInternalInventory(this, CELL_SLOTS, 1);
    private final AppEngInternalInventory outputCells = new AppEngInternalInventory(this, CELL_SLOTS, 1);
    private final InternalInventory combined = new CombinedInternalInventory(inputCells, outputCells);
    private final InternalInventory inputExternal = new FilteredInternalInventory(inputCells, AEItemFilters.INSERT_ONLY);
    private final InternalInventory outputExternal = new FilteredInternalInventory(outputCells, AEItemFilters.EXTRACT_ONLY);
    private final InternalInventory external = new CombinedInternalInventory(inputExternal, outputExternal);
    private final IUpgradeInventory upgrades = UpgradeInventories.forMachine(
            DEBlocks.DATA_IO_PORT.get(), UPGRADE_SLOTS, this::settingsChanged);
    private final IConfigManager config = IConfigManager.builder(this::settingsChanged)
            .registerSetting(Settings.REDSTONE_CONTROLLED, RedstoneMode.IGNORE)
            .registerSetting(Settings.FULLNESS_MODE, FullnessMode.EMPTY)
            .registerSetting(Settings.OPERATION_MODE, OperationMode.EMPTY)
            .build();
    private final MachineSource actionSource = new MachineSource(this);
    private final DataIoPortTransfer transfer = new DataIoPortTransfer(inputCells, outputCells, actionSource);
    private boolean redstonePowered;
    private boolean pulsePending;
    private boolean clientActive;
    private boolean failed;
    private boolean transferring;

    public DataIoPortBlockEntity(BlockPos pos, BlockState state) {
        super(DEBlockEntities.DATA_IO_PORT.get(), pos, state);
        getMainNode().setVisualRepresentation(DEBlocks.DATA_IO_PORT.get())
                .setIdlePowerUsage(0.0D)
                .setFlags(GridFlags.REQUIRE_CHANNEL)
                .addService(IGridTickable.class, this);
        inputCells.setFilter(new CellInputFilter());
    }

    @Override
    public AECableType getCableConnectionType(Direction direction) {
        return AECableType.SMART;
    }

    @Override
    public InternalInventory getInternalInventory() {
        return combined;
    }

    @Override
    protected InternalInventory getExposedInventoryForSide(Direction side) {
        return side == getTop() || side == getTop().getOpposite() ? inputExternal : outputExternal;
    }

    @Override
    public IItemHandler getExposedItemHandler(@Nullable Direction side) {
        return (side == null ? external : getExposedInventoryForSide(side)).toItemHandler();
    }

    @Override
    public IUpgradeInventory getUpgrades() {
        return upgrades;
    }

    @Override
    public IConfigManager getConfigManager() {
        return config;
    }

    @Override
    public @Nullable InternalInventory getSubInventory(ResourceLocation id) {
        if (ISegmentedInventory.UPGRADES.equals(id)) return upgrades;
        if (ISegmentedInventory.CELLS.equals(id)) return combined;
        return super.getSubInventory(id);
    }

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        return new TickingRequest(TickRates.IOPort, !hasWork());
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        if (transferring || !getMainNode().isActive()) return TickRateModulation.IDLE;
        if (!hasWork()) return TickRateModulation.SLEEP;
        var grid = node.getGrid();
        transferring = true;
        pulsePending = false;
        try {
            boolean changed = transfer.transfer(grid.getStorageService().getInventory(),
                    config.getSetting(Settings.OPERATION_MODE), config.getSetting(Settings.FULLNESS_MODE),
                    upgrades.getInstalledUpgrades(AEItems.SPEED_CARD),
                    upgrades.getInstalledUpgrades(DEItems.CARD_SABER_ENERGY.get()));
            return !hasWork() ? TickRateModulation.SLEEP : changed ? TickRateModulation.URGENT : TickRateModulation.IDLE;
        } catch (RuntimeException exception) {
            failed = true;
            Data_Energistics.LOGGER.error("Data IO port at {} in {} stopped during {} transfer", worldPosition,
                    level.dimension().location(), config.getSetting(Settings.OPERATION_MODE), exception);
            return TickRateModulation.SLEEP;
        } finally {
            transferring = false;
            saveChanges();
        }
    }

    public boolean isActive() {
        return level != null && level.isClientSide() ? clientActive : getMainNode().isActive();
    }

    private boolean hasWork() {
        return !failed && isEnabled() && (!inputCells.isEmpty() || transfer.hasPending());
    }

    private boolean isEnabled() {
        if (!upgrades.isInstalled(AEItems.REDSTONE_CARD)) return true;
        return switch (config.getSetting(Settings.REDSTONE_CONTROLLED)) {
            case IGNORE -> true;
            case LOW_SIGNAL -> !redstonePowered;
            case HIGH_SIGNAL -> redstonePowered;
            case SIGNAL_PULSE -> pulsePending;
        };
    }

    public void updateRedstoneState() {
        if (level == null || level.isClientSide()) return;
        boolean powered = level.hasNeighborSignal(worldPosition);
        if (powered != redstonePowered) {
            if (powered && upgrades.isInstalled(AEItems.REDSTONE_CARD) && config.getSetting(Settings.REDSTONE_CONTROLLED) == RedstoneMode.SIGNAL_PULSE) {
                pulsePending = true;
            }
            redstonePowered = powered;
            saveChanges();
            updateTask();
        }
    }

    private void updateTask() {
        getMainNode().ifPresent((grid, node) -> {
            if (hasWork()) grid.getTickManager().wakeDevice(node);
            else grid.getTickManager().sleepDevice(node);
        });
    }

    private void settingsChanged() {
        failed = false;
        pulsePending = false;
        saveChanges();
        updateTask();
    }

    @Override
    public void onChangeInventory(AppEngInternalInventory inventory, int slot) {
        if (inventory == inputCells) transfer.resetCell(slot);
        failed = false;
        updateTask();
    }

    @Override
    public void onMainNodeStateChanged(IGridNodeListener.State reason) {
        markForUpdate();
        updateTask();
    }

    @Override
    public void onReady() {
        super.onReady();
        redstonePowered = level.hasNeighborSignal(worldPosition);
        updateTask();
        markForUpdate();
    }

    @Override
    protected void writeToStream(RegistryFriendlyByteBuf data) {
        super.writeToStream(data);
        data.writeBoolean(isActive());
    }

    @Override
    protected boolean readFromStream(RegistryFriendlyByteBuf data) {
        boolean changed = super.readFromStream(data);
        boolean active = data.readBoolean();
        changed |= active != clientActive;
        clientActive = active;
        return changed;
    }

    @Override
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        config.writeToNBT(tag, registries);
        upgrades.writeToNBT(tag, "upgrades", registries);
        tag.put("transfer", transfer.save(registries));
        tag.putBoolean("pulse_pending", pulsePending);
        tag.putBoolean("failed", failed);
    }

    @Override
    public void loadTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadTag(tag, registries);
        config.readFromNBT(tag, registries);
        upgrades.readFromNBT(tag, "upgrades", registries);
        transfer.load(tag.getCompound("transfer"), registries);
        pulsePending = tag.getBoolean("pulse_pending");
        failed = tag.getBoolean("failed");
    }

    @Override
    public void exportSettings(SettingsFrom mode, DataComponentMap.Builder builder, @Nullable Player player) {
        super.exportSettings(mode, builder, player);
        if (mode == SettingsFrom.DISMANTLE_ITEM) {
            var data = new CompoundTag();
            config.writeToNBT(data, level.registryAccess());
            data.put("transfer", transfer.save(level.registryAccess()));
            var customData = new CompoundTag();
            customData.put(ITEM_STATE, data);
            builder.set(DataComponents.CUSTOM_DATA, CustomData.of(customData));
        }
    }

    @Override
    public void importSettings(SettingsFrom mode, DataComponentMap data, @Nullable Player player) {
        super.importSettings(mode, data, player);
        if (mode == SettingsFrom.DISMANTLE_ITEM) {
            var custom = data.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
            if (custom.contains(ITEM_STATE)) {
                var state = custom.getCompound(ITEM_STATE);
                config.readFromNBT(state, level.registryAccess());
                transfer.load(state.getCompound("transfer"), level.registryAccess());
                saveChanges();
                updateTask();
            }
        }
    }

    @Override
    public void addAdditionalDrops(Level level, BlockPos pos, List<ItemStack> drops) {
        super.addAdditionalDrops(level, pos, drops);
        for (ItemStack stack : upgrades) {
            if (!stack.isEmpty()) drops.add(stack);
        }
    }

    @Override
    public void clearContent() {
        super.clearContent();
        upgrades.clear();
        transfer.clear();
    }

    private static final class CellInputFilter implements IAEItemFilter {

        @Override
        public boolean allowInsert(InternalInventory inventory, int slot, ItemStack stack) {
            return StorageCells.getCellInventory(stack, null) != null;
        }

        @Override
        public boolean allowExtract(InternalInventory inventory, int slot, int amount) {
            return true;
        }
    }
}
