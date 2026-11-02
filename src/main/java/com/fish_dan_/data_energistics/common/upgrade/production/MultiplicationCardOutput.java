package com.fish_dan_.data_energistics.common.upgrade.production;

import com.fish_dan_.data_energistics.common.data.DEItems;

import appeng.api.inventories.InternalInventory;
import appeng.api.stacks.GenericStack;
import appeng.api.upgrades.IUpgradeInventory;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** Applies the installed card to newly generated outputs, never to stored stacks or shared recipes. */
@NullMarked
public final class MultiplicationCardOutput {

    // Vanilla ItemStack persistence accepts counts up to 99; a boosted ordinary stack needs at most 96.
    private static final int MAX_SAVED_STACK_COUNT = 99;

    private MultiplicationCardOutput() {}

    public static boolean isInstalled(IUpgradeInventory upgrades) {
        return upgrades.getInstalledUpgrades(DEItems.MULTIPLICATION_CARD.get()) > 0;
    }

    public static ItemStack doubleOutput(ItemStack output, IUpgradeInventory upgrades) {
        return output.isEmpty() || !isInstalled(upgrades) ? output :
                output.copyWithCount(Math.multiplyExact(output.getCount(), 2));
    }

    public static @Nullable GenericStack doubleOutput(@Nullable GenericStack output, IUpgradeInventory upgrades) {
        return output == null || !isInstalled(upgrades) ? output :
                new GenericStack(output.what(), Math.multiplyExact(output.amount(), 2L));
    }

    public static ItemStack boostExternalOutput(ItemStack output, IUpgradeInventory upgrades) {
        if (output.isEmpty()) {
            return output;
        }
        int amount = boostExternalAmount(output.getCount(), upgrades);
        return amount == output.getCount() ? output : output.copyWithCount(amount);
    }

    public static FluidStack boostExternalOutput(FluidStack output, IUpgradeInventory upgrades) {
        if (output.isEmpty()) {
            return output;
        }
        int amount = boostExternalAmount(output.getAmount(), upgrades);
        return amount == output.getAmount() ? output : output.copyWithAmount(amount);
    }

    public static int boostExternalAmount(int amount, IUpgradeInventory upgrades) {
        return Math.toIntExact(boostExternalAmount((long) amount, upgrades));
    }

    public static long boostExternalAmount(long amount, IUpgradeInventory upgrades) {
        if (amount < 0L) {
            throw new IllegalArgumentException("Production output amount cannot be negative");
        }
        return isInstalled(upgrades) ? Math.addExact(amount, amount / 2L) : amount;
    }

    /** Capacity of a trusted machine output slot; input slots retain their native inventory contract. */
    public static int externalOutputCapacity(InternalInventory inventory, int slot, ItemStack output, IUpgradeInventory upgrades) {
        int nativeCapacity = Math.min(inventory.getSlotLimit(slot), output.getMaxStackSize());
        return Math.min(MAX_SAVED_STACK_COUNT, boostExternalAmount(nativeCapacity, upgrades));
    }

    /** Simulates or commits one whole result so a failed completion can never leave a partial duplicate. */
    public static ItemStack insertExternalOutput(InternalInventory inventory, int slot, ItemStack output,
                                                 boolean simulate, IUpgradeInventory upgrades) {
        if (output.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack current = inventory.getStackInSlot(slot);
        if (!inventory.isItemValid(slot, output) ||
                !current.isEmpty() && !ItemStack.isSameItemSameComponents(current, output) ||
                (long) current.getCount() + output.getCount() > externalOutputCapacity(inventory, slot, output, upgrades)) {
            return output;
        }
        if (!simulate) {
            inventory.setItemDirect(slot, output.copyWithCount(current.getCount() + output.getCount()));
        }
        return ItemStack.EMPTY;
    }

    /** Restores only a just-extracted export remainder, including after its producing card has been removed. */
    public static ItemStack restoreExternalOutput(InternalInventory inventory, int slot, ItemStack output, boolean simulate) {
        if (output.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack current = inventory.getStackInSlot(slot);
        long restored = (long) current.getCount() + output.getCount();
        if (!current.isEmpty() && !ItemStack.isSameItemSameComponents(current, output) || restored > MAX_SAVED_STACK_COUNT) {
            throw new IllegalStateException("Cannot restore the complete machine output after a rejected export");
        }
        if (!simulate) {
            inventory.setItemDirect(slot, output.copyWithCount((int) restored));
        }
        return ItemStack.EMPTY;
    }
}
