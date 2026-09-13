package com.fish_dan_.data_energistics.ae2.sanctum;

import appeng.api.inventories.InternalInventory;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.Upgrades;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.InternalInventoryHost;
import appeng.util.inv.filter.IAEItemFilter;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

import it.unimi.dsi.fastutil.objects.Reference2IntArrayMap;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

public class FixedSizeMachineUpgradeInventory extends AppEngInternalInventory implements InternalInventoryHost, IUpgradeInventory {

    @Nullable
    private Reference2IntMap<Item> installed;

    private final Supplier<? extends Item> itemSupplier;
    @Nullable
    private final Runnable changeCallback;

    public FixedSizeMachineUpgradeInventory(ItemLike item, int slots, @Nullable Runnable changeCallback) {
        this((Supplier<Item>) item::asItem, slots, changeCallback);
    }

    /**
     * Creates an upgrade inventory whose supported cards follow the current machine item.
     * The supplier is evaluated whenever the upgrade state is queried, so a host can change
     * the represented machine without replacing the inventory object used by an open menu.
     */
    public FixedSizeMachineUpgradeInventory(Supplier<? extends Item> itemSupplier, int slots,
                                            @Nullable Runnable changeCallback) {
        super(null, slots, 1);
        this.itemSupplier = itemSupplier;
        this.changeCallback = changeCallback;
        this.setHost(this);
        this.setFilter(new UpgradeInvFilter());
    }

    @Override
    public boolean isClientSide() {
        return false;
    }

    @Override
    protected boolean eventsEnabled() {
        return true;
    }

    @Override
    public int getMaxInstalled(ItemLike upgradeCard) {
        return Upgrades.getMaxInstallable(upgradeCard, getUpgradableItem());
    }

    @Override
    public ItemLike getUpgradableItem() {
        return this.itemSupplier.get();
    }

    @Override
    public int getInstalledUpgrades(ItemLike upgradeCard) {
        if (this.installed == null || this.cachedItem != getUpgradableItem().asItem()) {
            updateUpgradeInfo();
        }
        return this.installed.getOrDefault(upgradeCard.asItem(), 0);
    }

    @Override
    public void readFromNBT(CompoundTag data, String subtag, HolderLookup.Provider registries) {
        super.readFromNBT(data, subtag, registries);
        updateUpgradeInfo();
    }

    @Override
    public void writeToNBT(CompoundTag data, String subtag, HolderLookup.Provider registries) {
        super.writeToNBT(data, subtag, registries);
    }

    @Override
    public void saveChangedInventory(AppEngInternalInventory inv) {}

    @Override
    public void onChangeInventory(AppEngInternalInventory inv, int slot) {
        this.installed = null;
        if (this.changeCallback != null) {
            this.changeCallback.run();
        }
    }

    @Override
    public void sendChangeNotification(int slot) {
        this.installed = null;
        super.sendChangeNotification(slot);
    }

    private void updateUpgradeInfo() {
        this.cachedItem = getUpgradableItem().asItem();
        this.installed = new Reference2IntArrayMap<>(size());
        for (ItemStack stack : this) {
            int maxInstalled = getMaxInstalled(stack.getItem());
            if (maxInstalled > 0) {
                this.installed.merge(stack.getItem(), 1, (a, b) -> Math.min(maxInstalled, a + b));
            }
        }
    }

    @Nullable
    private Item cachedItem;

    private class UpgradeInvFilter implements IAEItemFilter {

        @Override
        public boolean allowExtract(InternalInventory inv, int slot, int amount) {
            return true;
        }

        @Override
        public boolean allowInsert(InternalInventory inv, int slot, ItemStack itemstack) {
            Item cardItem = itemstack.getItem();
            return getInstalledUpgrades(cardItem) < getMaxInstalled(cardItem);
        }
    }
}
