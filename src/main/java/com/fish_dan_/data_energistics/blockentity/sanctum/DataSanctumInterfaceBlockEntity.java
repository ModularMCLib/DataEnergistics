package com.fish_dan_.data_energistics.blockentity.sanctum;

import com.fish_dan_.data_energistics.ae2.sanctum.DataSanctumInterfaceConstants;
import com.fish_dan_.data_energistics.ae2.sanctum.DataSanctumLargeInterfaceHost;
import com.fish_dan_.data_energistics.ae2.sanctum.DataSanctumReturnInventory;
import com.fish_dan_.data_energistics.ae2.sanctum.FixedSizeMachineUpgradeInventory;
import com.fish_dan_.data_energistics.ae2.sanctum.InterfaceStockLogic;
import com.fish_dan_.data_energistics.ae2.sanctum.connector.InterfaceRemoteLinks;
import com.fish_dan_.data_energistics.mixin.ae.ae2.accessor.InterfaceLogicUpgradesAccessor;
import com.fish_dan_.data_energistics.registry.DEBlockEntities;
import com.fish_dan_.data_energistics.registry.DEBlocks;
import com.fish_dan_.data_energistics.registry.DEMenus;

import appeng.api.inventories.ISegmentedInventory;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IManagedGridNode;
import appeng.api.orientation.BlockOrientation;
import appeng.api.util.AECableType;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import appeng.core.definitions.AEItems;
import appeng.helpers.InterfaceLogic;
import appeng.me.helpers.BlockEntityNodeListener;
import appeng.me.helpers.MachineSource;
import appeng.menu.ISubMenu;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuHostLocator;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import lombok.Getter;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public class DataSanctumInterfaceBlockEntity extends AENetworkedBlockEntity implements DataSanctumLargeInterfaceHost {

    private static final String RETURN_INVENTORY_TAG = "returnInv";

    private static final IGridNodeListener<DataSanctumInterfaceBlockEntity> NODE_LISTENER = new BlockEntityNodeListener<>() {

        @Override
        public void onGridChanged(DataSanctumInterfaceBlockEntity nodeOwner, IGridNode node) {
            nodeOwner.interfaceLogic.gridChanged();
        }
    };

    private final InterfaceLogic interfaceLogic = new InterfaceStockLogic(
            this.getMainNode(),
            this,
            DEBlocks.DATA_SANCTUM_INTERFACE.get().asItem());
    private final DataSanctumReturnInventory returnInventory = new DataSanctumReturnInventory(
            this::onReturnInventoryChanged,
            this::getInstalledCapacityCardCount);
    private final MachineSource actionSource = new MachineSource(this);
    @Getter
    private final InterfaceRemoteLinks remoteLinks = new InterfaceRemoteLinks(this, getMainNode(), actionSource, this::onRemoteLinksChanged);

    public DataSanctumInterfaceBlockEntity(BlockPos blockPos, BlockState blockState) {
        super(DEBlockEntities.DATA_SANCTUM_INTERFACE_BLOCK_ENTITY.get(), blockPos, blockState);
        expandUpgradeSlots();
        this.getMainNode()
                .setVisualRepresentation(DEBlocks.DATA_SANCTUM_INTERFACE.get())
                .setIdlePowerUsage(0.0D);
    }

    @Override
    protected IManagedGridNode createMainNode() {
        return GridHelper.createManagedNode(this, NODE_LISTENER);
    }

    @Override
    public Set<Direction> getGridConnectableSides(BlockOrientation orientation) {
        return EnumSet.allOf(Direction.class);
    }

    @Override
    public AECableType getCableConnectionType(Direction dir) {
        return AECableType.COVERED;
    }

    public boolean isOnline() {
        return this.getMainNode().isOnline();
    }

    @Override
    public void onMainNodeStateChanged(IGridNodeListener.State reason) {
        if (this.getMainNode().hasGridBooted()) {
            this.interfaceLogic.notifyNeighbors();
        }
    }

    @Override
    public InterfaceLogic getInterfaceLogic() {
        return this.interfaceLogic;
    }

    @Override
    public ItemStack getMainMenuIcon() {
        return DEBlocks.DATA_SANCTUM_INTERFACE.toStack();
    }

    @Override
    public void openMenu(Player player, MenuHostLocator locator) {
        MenuOpener.open(DEMenus.DATA_SANCTUM_LARGE_INTERFACE.get(), player, locator);
    }

    @Override
    public void returnToMainMenu(Player player, ISubMenu subMenu) {
        MenuOpener.returnTo(DEMenus.DATA_SANCTUM_LARGE_INTERFACE.get(), player, subMenu.getLocator());
    }

    @Override
    public void saveAdditional(CompoundTag data, HolderLookup.Provider registries) {
        super.saveAdditional(data, registries);
        this.interfaceLogic.writeToNBT(data, registries);
        this.returnInventory.writeToChildTag(data, RETURN_INVENTORY_TAG, registries);
        this.remoteLinks.write(data, registries);
    }

    @Override
    public void loadTag(CompoundTag data, HolderLookup.Provider registries) {
        super.loadTag(data, registries);
        this.interfaceLogic.readFromNBT(data, registries);
        this.returnInventory.readFromChildTag(data, RETURN_INVENTORY_TAG, registries);
        this.remoteLinks.read(data, registries);
    }

    @Override
    public void addAdditionalDrops(Level level, BlockPos pos, List<ItemStack> drops) {
        super.addAdditionalDrops(level, pos, drops);
        this.interfaceLogic.addDrops(drops);
        this.returnInventory.addDrops(drops, level, pos);
        this.remoteLinks.addDrops(drops, level, pos);
    }

    @Override
    public void clearContent() {
        super.clearContent();
        this.interfaceLogic.clearContent();
        this.returnInventory.clear();
        this.remoteLinks.clearContent();
    }

    @Override
    public InternalInventory getSubInventory(ResourceLocation id) {
        if (ISegmentedInventory.UPGRADES.equals(id)) {
            return this.interfaceLogic.getUpgrades();
        }
        return super.getSubInventory(id);
    }

    private void expandUpgradeSlots() {
        InterfaceLogicUpgradesAccessor accessor = (InterfaceLogicUpgradesAccessor) this.interfaceLogic;
        accessor.dataEnergistics$setUpgradesField(new FixedSizeMachineUpgradeInventory(
                DEBlocks.DATA_SANCTUM_INTERFACE.get(),
                DataSanctumInterfaceConstants.UPGRADE_SLOT_COUNT,
                () -> {
                    accessor.dataEnergistics$invokeOnUpgradesChanged();
                    this.interfaceLogic.onConfigRowChanged();
                    this.markForClientUpdate();
                }));
    }

    public DataSanctumReturnInventory getReturnInventory() {
        return this.returnInventory;
    }

    public int getInstalledCapacityCardCount() {
        return Math.max(0, Math.min(
                DataSanctumInterfaceConstants.MAX_CAPACITY_CARDS,
                this.interfaceLogic.getUpgrades().getInstalledUpgrades(AEItems.CAPACITY_CARD)));
    }

    @Override
    public Level getInterfaceLevel() {
        return this.level;
    }

    @Override
    public BlockPos getInterfaceBlockPos() {
        return this.worldPosition;
    }

    public void serverTick() {
        if (this.level == null || this.level.isClientSide()) {
            return;
        }

        this.interfaceLogic.updateStorage();
        injectReturnInventory();
        this.remoteLinks.tick();
    }

    private void onRemoteLinksChanged() {
        this.saveChanges();
        this.markForClientUpdate();
    }

    @Override
    protected void writeToStream(RegistryFriendlyByteBuf data) {
        super.writeToStream(data);
        this.remoteLinks.writeToStream(data);
    }

    @Override
    protected boolean readFromStream(RegistryFriendlyByteBuf data) {
        boolean changed = super.readFromStream(data);
        return this.remoteLinks.readFromStream(data) || changed;
    }

    private void onReturnInventoryChanged() {
        this.getMainNode().ifPresent((grid, node) -> grid.getTickManager().alertDevice(node));
        this.saveChanges();
    }

    private void injectReturnInventory() {
        IGridNode node = this.getMainNode().getNode();
        if (node == null || node.getGrid() == null || this.returnInventory.isEmpty()) {
            return;
        }

        this.returnInventory.injectIntoNetwork(node.getGrid().getStorageService().getInventory(), this.actionSource);
    }
}
