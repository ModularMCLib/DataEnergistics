package com.fish_dan_.data_energistics.common.terminal.player.storage;

import com.fish_dan_.data_energistics.common.terminal.player.connection.TerminalConnection;
import com.fish_dan_.data_energistics.common.terminal.player.persistence.TerminalPlayerState;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.StorageHelper;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Powered AE operations keep the physical cursor/inventory authoritative on the server.
 */
public final class TerminalNetworkStorage {

    private TerminalNetworkStorage() {}

    /** Server-thread return: only accepted items leave their source slots, and any remainder stays available. */
    public static boolean insertInventory(TerminalConnection connection, SimpleContainer inventory) {
        boolean complete = true;
        for (int index = 0; index < inventory.getContainerSize(); index++) {
            ItemStack stack = inventory.getItem(index);
            if (stack.isEmpty()) continue;
            int requested = stack.getCount();
            long inserted = StorageHelper.poweredInsert(connection.grid().getEnergyService(), connection.storage(), AEItemKey.of(stack), requested, connection.source());
            checkAmount(inserted, requested);
            if (inserted > 0) inventory.removeItem(index, (int) inserted);
            complete &= inserted == requested;
        }
        return complete;
    }

    /** Main-thread check for real main-inventory and hotbar slots, excluding equipment and configuration slots. */
    public static boolean isPlayerInventorySlot(Player player, Slot slot) {
        int index = slot.getContainerSlot();
        return slot.container == player.getInventory() && index >= 0 && index < player.getInventory().items.size();
    }

    /**
     * Server-thread operation after session and connection validation. Stale keys leave the slot untouched;
     * only the amount accepted by ME is removed from the player's inventory.
     */
    public static void insertInventorySlot(ServerPlayer player, TerminalConnection connection, int slotId, AEKey expected) {
        AbstractContainerMenu menu = player.containerMenu;
        if (slotId < 0 || slotId >= menu.slots.size()) throw new IllegalArgumentException("Invalid terminal source slot");
        Slot slot = menu.getSlot(slotId);
        if (!isPlayerInventorySlot(player, slot) || !slot.isActive() || !slot.mayPickup(player)) return;
        ItemStack stack = slot.getItem();
        if (!(expected instanceof AEItemKey item) || !item.matches(stack) || GenericStack.isWrapped(stack)) return;
        int requested = stack.getCount();
        long inserted = StorageHelper.poweredInsert(connection.grid().getEnergyService(), connection.storage(), item, requested, connection.source());
        checkAmount(inserted, requested);
        if (inserted == 0) return;
        ItemStack transferred = stack.copyWithCount((int) inserted);
        slot.setByPlayer(stack.copyWithCount(requested - (int) inserted));
        slot.onTake(player, transferred);
    }

    /** Server-thread stow of a real host slot, honoring native take callbacks and preserving any rejected remainder. */
    public static void stowSlot(ServerPlayer player, TerminalPlayerState state, TerminalConnection connection, int slotId, AEKey expected) {
        var menu = player.containerMenu;
        if (slotId < 0 || slotId >= menu.slots.size()) throw new IllegalArgumentException("Invalid terminal stow slot");
        Slot slot = menu.getSlot(slotId);
        if (!slot.isActive() || slot.isFake() || !slot.mayPickup(player)) return;
        ItemStack stack = slot.getItem();
        if (!(expected instanceof AEItemKey item) || !item.matches(stack) || GenericStack.isWrapped(stack)) return;
        long allowed = StorageHelper.poweredInsert(connection.grid().getEnergyService(), connection.storage(), item, stack.getCount(), connection.source(), Actionable.SIMULATE);
        checkAmount(allowed, stack.getCount());
        if (allowed == 0) return;
        ItemStack taken = slot.safeTake((int) allowed, (int) allowed, player);
        if (taken.isEmpty()) return;
        int requested = taken.getCount();
        state.returns().extracted(connection.frequency(), item, requested);
        long inserted = StorageHelper.poweredInsert(connection.grid().getEnergyService(), connection.storage(), item, requested, connection.source());
        checkAmount(inserted, requested);
        state.returns().delivered(connection.frequency(), item, inserted);
        if (inserted < requested) {
            ItemStack remaining = slot.safeInsert(taken.copyWithCount(requested - (int) inserted));
            if (!remaining.isEmpty()) player.getInventory().add(remaining);
            state.returns().delivered(connection.frequency(), item, requested - inserted - remaining.getCount());
        }
    }

    public static int inventoryCapacity(ServerPlayer player, ItemStack stack) {
        int capacity = 0;
        for (int index = 0; index < player.getInventory().items.size(); index++) {
            ItemStack existing = player.getInventory().getItem(index);
            if (existing.isEmpty()) capacity += stack.getMaxStackSize();
            else if (ItemStack.isSameItemSameComponents(existing, stack))
                capacity += Math.max(0, existing.getMaxStackSize() - existing.getCount());
        }
        return capacity;
    }

    public static void refund(TerminalConnection connection, TerminalPlayerState state) {
        state.returns().refund(connection);
        if (state.paymentFrequency() == connection.frequency())
            state.anvilPayment().refund(connection.grid(), connection.source(), state::changed);
    }

    public static void checkAmount(long actual, long requested) {
        if (actual < 0 || actual > requested)
            throw new IllegalStateException("Invalid AE transfer result " + actual + " for " + requested);
    }
}
