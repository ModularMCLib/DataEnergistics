package com.fish_dan_.data_energistics.ae2.sanctum;

import com.fish_dan_.data_energistics.ae2.sanctum.inventory.MappedInterfaceInventory;

import appeng.api.config.Actionable;
import appeng.api.config.Settings;
import appeng.api.networking.IManagedGridNode;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.core.definitions.AEItems;
import appeng.helpers.InterfaceLogic;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Long stock reconciliation for the large interface, with network mappings excluded from restocking and drops. */
public final class InterfaceStockLogic extends InterfaceLogic {

    public InterfaceStockLogic(IManagedGridNode node, DataSanctumLargeInterfaceHost host, Item item) {
        super(node, host, item, DataSanctumInterfaceConstants.LOGIC_SLOT_COUNT);
        this.config = DataSanctumInterfaceInventory.config(this::onConfigRowChanged, host::getInstalledCapacityCardCount);
        this.storage = new MappedInterfaceInventory((DataSanctumInterfaceInventory) config, node,
                this.interfaceRequestSource, this::isAllowedInStorageSlot, this::onStorageChanged,
                host::getInstalledCapacityCardCount);
    }

    public MappedInterfaceInventory mappedStock() {
        return (MappedInterfaceInventory) storage;
    }

    public DataSanctumInterfaceInventory slotConfig() {
        return (DataSanctumInterfaceInventory) config;
    }

    @Override
    public boolean updateStorage() {
        if (!mainNode.isActive()) return false;
        boolean changed = false;
        var stock = mappedStock();
        for (int slot = 0; slot < stock.size(); slot++) {
            var requested = config.getStack(slot);
            var stored = stock.physicalStack(slot);
            boolean mapped = stock.isMapped(slot);
            // Locked pages retain their stock until they are unlocked again.
            if (!slotConfig().isSlotUnlocked(slot)) continue;
            long wanted = requested == null || mapped ? 0 : requested.amount();
            boolean matches = requested != null && stored != null && matches(requested.what(), stored.what());
            long surplus = stored == null ? 0 : matches ? Math.max(0, stored.amount() - wanted) : stored.amount();
            if (surplus > 0) {
                long returned = stock.networkInsert(stored.what(), surplus, Actionable.MODULATE);
                if (returned > 0) {
                    long remaining = stored.amount() - returned;
                    stock.setPhysicalStack(slot, remaining == 0 ? null : new GenericStack(stored.what(), remaining));
                    changed = true;
                }
                // Retry a refused return next tick; never erase the contents or overwrite their key.
                continue;
            }
            if (mapped || requested == null) continue;
            long present = stored == null ? 0 : stored.amount();
            long missing = wanted - present;
            if (missing <= 0) continue;
            AEKey key = stored == null ? requested.what() : stored.what();
            long acquired = stock.networkExtract(key, missing, Actionable.MODULATE);
            if (acquired == 0 && stored == null && getUpgrades().isInstalled(AEItems.FUZZY_CARD)) {
                var grid = mainNode.getGrid();
                for (var candidate : grid.getStorageService().getCachedInventory().findFuzzy(
                        requested.what(), getConfigManager().getSetting(Settings.FUZZY_MODE))) {
                    key = candidate.getKey();
                    acquired = stock.networkExtract(key, missing, Actionable.MODULATE);
                    if (acquired > 0) break;
                }
            }
            if (acquired > 0) {
                stock.setPhysicalStack(slot, new GenericStack(key, present + acquired));
                changed = true;
            } else {
                changed |= handleCrafting(slot, requested.what(), missing);
            }
        }
        return changed;
    }

    private boolean matches(AEKey requested, AEKey stored) {
        return getUpgrades().isInstalled(AEItems.FUZZY_CARD) && requested.supportsFuzzyRangeSearch() ? requested.fuzzyEquals(stored, getConfigManager().getSetting(Settings.FUZZY_MODE)) : requested.equals(stored);
    }

    @Override
    public void addDrops(List<ItemStack> drops) {
        for (var upgrade : getUpgrades()) {
            if (!upgrade.isEmpty()) drops.add(upgrade);
        }
        for (int slot = 0; slot < storage.size(); slot++) {
            var stack = mappedStock().physicalStack(slot);
            if (stack != null) {
                stack.what().addDrops(stack.amount(), drops, host.getBlockEntity().getLevel(), host.getBlockEntity().getBlockPos());
            }
        }
    }
}
