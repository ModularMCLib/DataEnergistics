package com.fish_dan_.data_energistics.common.terminal.player.storage;

import com.fish_dan_.data_energistics.common.terminal.player.connection.TerminalConnection;
import com.fish_dan_.data_energistics.common.terminal.player.persistence.TerminalPlayerState;

import appeng.api.behaviors.ContainerItemStrategies;
import appeng.api.config.Actionable;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.StorageHelper;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/**
 * Powered AE operations keep the physical cursor/inventory authoritative on the server.
 */
public final class TerminalNetworkStorage {

    private TerminalNetworkStorage() {}

    public static void click(ServerPlayer player, TerminalConnection connection, TerminalPlayerState state, AEKey key, int button, boolean shift) {
        if (button != 0 && button != 1) throw new IllegalArgumentException("Invalid storage button");
        AbstractContainerMenu menu = player.containerMenu;
        if (!(key instanceof AEItemKey item)) {
            if (ContainerItemStrategies.isKeySupported(key)) {
                if (button == 0) fillContainer(player, connection, state, key, shift);
                else emptyContainer(player, connection, state, key, shift);
            }
            return;
        }
        ItemStack cursor = menu.getCarried();
        if (!cursor.isEmpty()) {
            insertCursor(player, connection, button == 1 ? 1 : cursor.getCount());
            return;
        }
        ItemStack sample = item.toStack(1);
        int wanted = button == 1 ? 1 : sample.getMaxStackSize();
        if (shift) wanted = Math.min(wanted, inventoryCapacity(player, sample));
        if (wanted == 0) return;
        long extracted = StorageHelper.poweredExtraction(connection.grid().getEnergyService(), connection.storage(), key, wanted, connection.source());
        checkAmount(extracted, wanted);
        if (extracted == 0) return;
        state.returns().extracted(connection.frequency(), key, extracted);
        ItemStack taken = item.toStack((int) extracted);
        if (shift) player.getInventory().add(taken);
        else {
            menu.setCarried(taken);
            taken = ItemStack.EMPTY;
        }
        state.returns().delivered(connection.frequency(), key, extracted - taken.getCount());
        refund(connection, state);
    }

    public static void insertCursor(ServerPlayer player, TerminalConnection connection, int limit) {
        ItemStack cursor = player.containerMenu.getCarried();
        GenericStack resource = GenericStack.fromItemStack(cursor);
        if (resource == null || GenericStack.isWrapped(cursor)) return;
        long amount = Math.min(limit, cursor.getCount());
        long inserted = StorageHelper.poweredInsert(connection.grid().getEnergyService(), connection.storage(), resource.what(), amount, connection.source());
        checkAmount(inserted, amount);
        cursor.shrink((int) inserted);
        player.containerMenu.setCarried(cursor.isEmpty() ? ItemStack.EMPTY : cursor);
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

    private static void fillContainer(ServerPlayer player, TerminalConnection connection, TerminalPlayerState state, AEKey key, boolean all) {
        for (int attempt = 0; attempt < (all ? 64 : 1); attempt++) {
            var context = ContainerItemStrategies.findCarriedContextForKey(key, player, player.containerMenu);
            if (context == null) return;
            long requested = all ? Long.MAX_VALUE : key.getAmountPerUnit();
            long capacity = context.insert(key, requested, Actionable.SIMULATE);
            checkAmount(capacity, requested);
            if (capacity == 0) return;
            long available = StorageHelper.poweredExtraction(connection.grid().getEnergyService(), connection.storage(), key, capacity, connection.source(), Actionable.SIMULATE);
            checkAmount(available, capacity);
            long amount = context.insert(key, available, Actionable.SIMULATE);
            checkAmount(amount, available);
            if (amount == 0) return;
            long extracted = StorageHelper.poweredExtraction(connection.grid().getEnergyService(), connection.storage(), key, amount, connection.source());
            checkAmount(extracted, amount);
            if (extracted == 0) return;
            state.returns().extracted(connection.frequency(), key, extracted);
            long accepted = context.insert(key, extracted, Actionable.MODULATE);
            checkAmount(accepted, extracted);
            state.returns().delivered(connection.frequency(), key, accepted);
            refund(connection, state);
            if (accepted > 0) context.playFillSound(player, key);
            if (accepted != extracted) return;
        }
    }

    private static void emptyContainer(ServerPlayer player, TerminalConnection connection, TerminalPlayerState state, AEKey key, boolean all) {
        for (int attempt = 0; attempt < (all ? 64 : 1); attempt++) {
            var context = ContainerItemStrategies.findCarriedContextForKey(key, player, player.containerMenu);
            if (context == null) return;
            GenericStack content = context.getExtractableContent();
            if (content == null || !content.what().equals(key)) return;
            long requested = all ? content.amount() : Math.min(content.amount(), key.getAmountPerUnit());
            long available = context.extract(key, requested, Actionable.SIMULATE);
            checkAmount(available, requested);
            long capacity = StorageHelper.poweredInsert(connection.grid().getEnergyService(), connection.storage(), key, available, connection.source(), Actionable.SIMULATE);
            checkAmount(capacity, available);
            long amount = context.extract(key, capacity, Actionable.SIMULATE);
            checkAmount(amount, capacity);
            if (amount == 0) return;
            long extracted = context.extract(key, amount, Actionable.MODULATE);
            checkAmount(extracted, amount);
            if (extracted == 0) return;
            state.returns().extracted(connection.frequency(), key, extracted);
            long accepted = StorageHelper.poweredInsert(connection.grid().getEnergyService(), connection.storage(), key, extracted, connection.source());
            checkAmount(accepted, extracted);
            state.returns().delivered(connection.frequency(), key, accepted);
            if (accepted < extracted) {
                long returned = context.insert(key, extracted - accepted, Actionable.MODULATE);
                checkAmount(returned, extracted - accepted);
                state.returns().delivered(connection.frequency(), key, returned);
            }
            refund(connection, state);
            if (accepted > 0) context.playEmptySound(player, key);
            if (accepted != extracted) return;
        }
    }

    public static void checkAmount(long actual, long requested) {
        if (actual < 0 || actual > requested)
            throw new IllegalStateException("Invalid AE transfer result " + actual + " for " + requested);
    }
}
