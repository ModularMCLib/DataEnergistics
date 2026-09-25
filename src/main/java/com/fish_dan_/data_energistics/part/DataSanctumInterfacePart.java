package com.fish_dan_.data_energistics.part;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.ae2.sanctum.DataSanctumInterfaceConstants;
import com.fish_dan_.data_energistics.ae2.sanctum.DataSanctumLargeInterfaceHost;
import com.fish_dan_.data_energistics.ae2.sanctum.DataSanctumReturnInventory;
import com.fish_dan_.data_energistics.ae2.sanctum.FixedSizeMachineUpgradeInventory;
import com.fish_dan_.data_energistics.ae2.sanctum.InterfaceStockLogic;
import com.fish_dan_.data_energistics.mixin.ae.ae2.accessor.InterfaceLogicTickAccessor;
import com.fish_dan_.data_energistics.mixin.ae.ae2.accessor.InterfaceLogicUpgradesAccessor;
import com.fish_dan_.data_energistics.registry.DEMenus;

import appeng.api.inventories.ISegmentedInventory;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.parts.IPartCollisionHelper;
import appeng.api.parts.IPartItem;
import appeng.api.parts.IPartModel;
import appeng.api.util.AECableType;
import appeng.core.definitions.AEItems;
import appeng.helpers.InterfaceLogic;
import appeng.items.parts.PartModels;
import appeng.me.helpers.MachineSource;
import appeng.menu.ISubMenu;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuHostLocator;
import appeng.menu.locator.MenuLocators;
import appeng.parts.AEBasePart;
import appeng.parts.PartModel;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

import java.util.List;

public class DataSanctumInterfacePart extends AEBasePart implements DataSanctumLargeInterfaceHost, IGridTickable {

    private static final String RETURN_INVENTORY_TAG = "returnInv";
    private static final ResourceLocation MODEL_BASE = ResourceLocation.fromNamespaceAndPath(
            Data_Energistics.MODID,
            "part/data_sanctum_interface_base");

    private static final IGridNodeListener<DataSanctumInterfacePart> NODE_LISTENER = new NodeListener<>() {

        @Override
        public void onGridChanged(DataSanctumInterfacePart nodeOwner, IGridNode node) {
            super.onGridChanged(nodeOwner, node);
            nodeOwner.interfaceLogic.gridChanged();
        }
    };

    @PartModels
    private static final PartModel MODELS_OFF;
    @PartModels
    private static final PartModel MODELS_ON;
    @PartModels
    private static final PartModel MODELS_HAS_CHANNEL;

    static {
        MODELS_OFF = new PartModel(MODEL_BASE, ResourceLocation.fromNamespaceAndPath(
                Data_Energistics.MODID,
                "part/data_sanctum_interface_off"));
        MODELS_ON = new PartModel(MODEL_BASE, ResourceLocation.fromNamespaceAndPath(
                Data_Energistics.MODID,
                "part/data_sanctum_interface_on"));
        MODELS_HAS_CHANNEL = new PartModel(MODEL_BASE, ResourceLocation.fromNamespaceAndPath(
                Data_Energistics.MODID,
                "part/data_sanctum_interface_has_channel"));
    }

    private final InterfaceLogic interfaceLogic = createLogic();
    private final DataSanctumReturnInventory returnInventory = new DataSanctumReturnInventory(
            this::onReturnInventoryChanged,
            this::getInstalledCapacityCardCount);
    private final MachineSource actionSource = new MachineSource(this);

    public DataSanctumInterfacePart(IPartItem<?> partItem) {
        super(partItem);
        expandUpgradeSlots();
        getMainNode().addService(IGridTickable.class, this);
    }

    protected InterfaceLogic createLogic() {
        return new InterfaceStockLogic(
                getMainNode(),
                this,
                getPartItem().asItem());
    }

    @Override
    protected IManagedGridNode createMainNode() {
        return GridHelper.createManagedNode(this, NODE_LISTENER)
                .setIdlePowerUsage(0.0D)
                .addService(IGridTickable.class, this);
    }

    @Override
    protected void onMainNodeStateChanged(IGridNodeListener.State reason) {
        super.onMainNodeStateChanged(reason);
        if (getMainNode().hasGridBooted()) {
            this.interfaceLogic.notifyNeighbors();
        }
    }

    @Override
    public void getBoxes(IPartCollisionHelper bch) {
        bch.addBox(2, 2, 14, 14, 14, 16);
        bch.addBox(5, 5, 12, 11, 11, 14);
    }

    @Override
    public void readFromNBT(CompoundTag data, HolderLookup.Provider registries) {
        super.readFromNBT(data, registries);
        this.interfaceLogic.readFromNBT(data, registries);
        this.returnInventory.readFromChildTag(data, RETURN_INVENTORY_TAG, registries);
    }

    @Override
    public void writeToNBT(CompoundTag data, HolderLookup.Provider registries) {
        super.writeToNBT(data, registries);
        this.interfaceLogic.writeToNBT(data, registries);
        this.returnInventory.writeToChildTag(data, RETURN_INVENTORY_TAG, registries);
    }

    @Override
    public void addAdditionalDrops(List<ItemStack> drops, boolean wrenched) {
        super.addAdditionalDrops(drops, wrenched);
        this.interfaceLogic.addDrops(drops);
        Level level = getInterfaceLevel();
        if (level != null) {
            this.returnInventory.addDrops(drops, level, getInterfaceBlockPos());
        }
    }

    @Override
    public void clearContent() {
        super.clearContent();
        this.interfaceLogic.clearContent();
        this.returnInventory.clear();
    }

    @Override
    public float getCableConnectionLength(AECableType cable) {
        return 4;
    }

    @Nullable
    @Override
    public InternalInventory getSubInventory(ResourceLocation id) {
        if (ISegmentedInventory.UPGRADES.equals(id)) {
            return this.interfaceLogic.getUpgrades();
        }
        return super.getSubInventory(id);
    }

    @Override
    public boolean onUseWithoutItem(Player player, Vec3 pos) {
        if (!player.getCommandSenderWorld().isClientSide()) {
            openMenu(player, MenuLocators.forPart(this));
        }
        return true;
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
    public InterfaceLogic getInterfaceLogic() {
        return this.interfaceLogic;
    }

    @Override
    public DataSanctumReturnInventory getReturnInventory() {
        return this.returnInventory;
    }

    @Override
    public int getInstalledCapacityCardCount() {
        return Math.max(0, Math.min(
                DataSanctumInterfaceConstants.MAX_CAPACITY_CARDS,
                this.interfaceLogic.getUpgrades().getInstalledUpgrades(AEItems.CAPACITY_CARD)));
    }

    @Override
    public @Nullable Level getInterfaceLevel() {
        BlockEntity blockEntity = getBlockEntity();
        return blockEntity != null ? blockEntity.getLevel() : null;
    }

    @Override
    public BlockPos getInterfaceBlockPos() {
        BlockEntity blockEntity = getBlockEntity();
        return blockEntity != null ? blockEntity.getBlockPos() : BlockPos.ZERO;
    }

    @Override
    public ItemStack getMainMenuIcon() {
        return new ItemStack(getPartItem().asItem());
    }

    @Override
    public IPartModel getStaticModels() {
        if (this.isActive() && this.isPowered()) {
            return MODELS_HAS_CHANNEL;
        } else if (this.isPowered()) {
            return MODELS_ON;
        } else {
            return MODELS_OFF;
        }
    }

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        return new TickingRequest(1, 1, false);
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        if (!isActive()) {
            return TickRateModulation.SLEEP;
        }

        injectReturnInventory();
        InterfaceLogicTickAccessor logicTickAccessor = (InterfaceLogicTickAccessor) this.interfaceLogic;
        boolean stocked = logicTickAccessor.dataEnergistics$invokeUpdateStorage();
        boolean hasStockWork = logicTickAccessor.dataEnergistics$invokeHasWorkToDo();
        if (hasStockWork) {
            return stocked ? TickRateModulation.URGENT : TickRateModulation.SLOWER;
        }
        return TickRateModulation.IDLE;
    }

    @Override
    public void saveChanges() {
        if (getHost() != null) {
            getHost().markForSave();
        }
    }

    private void expandUpgradeSlots() {
        InterfaceLogicUpgradesAccessor accessor = (InterfaceLogicUpgradesAccessor) this.interfaceLogic;
        accessor.dataEnergistics$setUpgradesField(new FixedSizeMachineUpgradeInventory(
                getPartItem().asItem(),
                DataSanctumInterfaceConstants.UPGRADE_SLOT_COUNT,
                () -> {
                    accessor.dataEnergistics$invokeOnUpgradesChanged();
                    this.interfaceLogic.onConfigRowChanged();
                    this.markForClientUpdate();
                }));
    }

    private void markForClientUpdate() {
        if (getHost() != null) {
            getHost().markForUpdate();
        }
    }

    private void onReturnInventoryChanged() {
        IGridNode node = this.getMainNode().getNode();
        if (node != null && node.getGrid() != null) {
            node.getGrid().getTickManager().alertDevice(node);
        }
        saveChanges();
    }

    private void injectReturnInventory() {
        IGridNode node = this.getMainNode().getNode();
        if (node == null || node.getGrid() == null || this.returnInventory.isEmpty()) {
            return;
        }

        this.returnInventory.injectIntoNetwork(node.getGrid().getStorageService().getInventory(), this.actionSource);
    }
}
