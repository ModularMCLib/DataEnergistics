package com.fish_dan_.data_energistics.api.upgrade.production;

import com.fish_dan_.data_energistics.common.upgrade.production.MultiplicationCardOutput;

import appeng.api.inventories.InternalInventory;
import appeng.api.upgrades.IUpgradeInventory;

import net.minecraft.world.item.ItemStack;

import org.jspecify.annotations.NullMarked;

/**
 * Public production-card operations for optional native compatibility wrappers. Invoke on the owning server thread
 * with non-null current inventories. Boost only newly generated results; stored stacks must never be boosted again.
 */
@NullMarked
public final class MultiplicationCardOutputs {

    private MultiplicationCardOutputs() {}

    /** Reads the current card state, including upgrades changed while the machine is running. */
    public static boolean isInstalled(IUpgradeInventory upgrades) {
        return MultiplicationCardOutput.isInstalled(upgrades);
    }

    /** Returns a boosted copy of a generated result, or the original when empty or without a card. */
    public static ItemStack boost(ItemStack output, IUpgradeInventory upgrades) {
        return MultiplicationCardOutput.boostExternalOutput(output, upgrades);
    }

    /** Boosts a non-negative generated amount; negative amounts or overflow throw at this boundary. */
    public static long boostAmount(long amount, IUpgradeInventory upgrades) {
        return MultiplicationCardOutput.boostExternalAmount(amount, upgrades);
    }

    /** Boosts newly generated AE power while retaining its native fractional precision. */
    public static double boostPower(double amount, IUpgradeInventory upgrades) {
        return MultiplicationCardOutput.boostExternalPower(amount, upgrades);
    }

    /** Returns the card-adjusted capacity of a valid trusted output slot, within saved stack count limits. */
    public static int capacity(InternalInventory inventory, int slot, ItemStack output, IUpgradeInventory upgrades) {
        return MultiplicationCardOutput.externalOutputCapacity(inventory, slot, output, upgrades);
    }

    /** Inserts one complete result into a valid output slot; failure or simulation leaves the inventory unchanged. */
    public static ItemStack insert(InternalInventory inventory, int slot, ItemStack output,
                                   boolean simulate, IUpgradeInventory upgrades) {
        return MultiplicationCardOutput.insertExternalOutput(inventory, slot, output, simulate, upgrades);
    }

    /**
     * Restores a just-extracted rejected export to its original slot, including after removal of the card.
     * Returns an empty stack on success; mismatched stacks or exceeding the saved count limit throw.
     */
    public static ItemStack restore(InternalInventory inventory, int slot, ItemStack output, boolean simulate) {
        return MultiplicationCardOutput.restoreExternalOutput(inventory, slot, output, simulate);
    }
}
