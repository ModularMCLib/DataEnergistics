package com.fish_dan_.data_energistics.common.terminal.player.storage;

import com.fish_dan_.data_energistics.common.terminal.player.connection.TerminalConnection;
import com.fish_dan_.data_energistics.common.terminal.player.persistence.TerminalPlayerState;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.storage.StorageHelper;
import appeng.helpers.InventoryAction;

import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** Server-thread storage gestures for a validated overlay session; the actual host retains its cursor and slots. */
@NullMarked
public final class TerminalStorageActions {

    private TerminalStorageActions() {}

    /** Uses AE's gesture amounts and player-inventory order without constructing or opening another menu. */
    public static void interact(ServerPlayer player, TerminalPlayerState state, TerminalConnection connection,
                                @Nullable AEKey key, InventoryAction action) {
        ObjectList<ItemStack> previousInventory = new ObjectArrayList<>(player.getInventory().items.size());
        for (ItemStack stack : player.getInventory().items) previousInventory.add(stack.copy());
        switch (action) {
            case FILL_ITEM, FILL_ITEM_MOVE_TO_PLAYER, FILL_ENTIRE_ITEM, FILL_ENTIRE_ITEM_MOVE_TO_PLAYER -> {
                if (key != null) TerminalContainerItemActions.fill(player, state, connection, key,
                        action == InventoryAction.FILL_ITEM_MOVE_TO_PLAYER || action == InventoryAction.FILL_ENTIRE_ITEM_MOVE_TO_PLAYER,
                        action == InventoryAction.FILL_ENTIRE_ITEM || action == InventoryAction.FILL_ENTIRE_ITEM_MOVE_TO_PLAYER);
            }
            case EMPTY_ITEM, EMPTY_ENTIRE_ITEM -> TerminalContainerItemActions.empty(player, state, connection, action == InventoryAction.EMPTY_ENTIRE_ITEM);
            case PICKUP_OR_SET_DOWN, SPLIT_OR_PLACE_SINGLE -> {
                if (key == null || key instanceof AEItemKey) {
                    if (!player.containerMenu.getCarried().isEmpty()) insertCursor(player, connection, action == InventoryAction.SPLIT_OR_PLACE_SINGLE);
                    else if (key instanceof AEItemKey item) extractCursor(player, connection, item, action == InventoryAction.SPLIT_OR_PLACE_SINGLE);
                }
            }
            case ROLL_DOWN -> {
                if (key == null || key instanceof AEItemKey) insertCursor(player, connection, true);
            }
            case ROLL_UP, PICKUP_SINGLE -> {
                if (key instanceof AEItemKey item) extractSingle(player, connection, item);
            }
            case SHIFT_CLICK, MOVE_REGION -> {
                if (key instanceof AEItemKey item) {
                    ObjectList<Slot> slots = playerSlots(player);
                    int attempts = action == InventoryAction.MOVE_REGION ? player.getInventory().items.size() : 1;
                    for (int index = 0; index < attempts; index++)
                        if (!moveStackToPlayer(connection, item, slots)) break;
                }
            }
            case CREATIVE_DUPLICATE -> {
                if (player.getAbilities().instabuild && key instanceof AEItemKey item)
                    player.containerMenu.setCarried(item.toStack(item.getMaxStackSize()));
            }
            default -> throw new IllegalArgumentException("Unsupported terminal storage action: " + action);
        }
        // Some hosts do not expose the player's inventory slots. Native inventory updates use inventory
        // indices, so those hosts and the creative inventory receive the same committed changes.
        for (int index = 0; index < previousInventory.size(); index++) {
            ItemStack stack = player.getInventory().getItem(index);
            if (!ItemStack.matches(previousInventory.get(index), stack))
                player.connection.send(new ClientboundContainerSetSlotPacket(-2, 0, index, stack));
        }
    }

    private static void insertCursor(ServerPlayer player, TerminalConnection connection, boolean single) {
        ItemStack carried = player.containerMenu.getCarried();
        AEItemKey key = AEItemKey.of(carried);
        if (key == null) return;
        int requested = single ? 1 : carried.getCount();
        long inserted = StorageHelper.poweredInsert(connection.grid().getEnergyService(), connection.storage(), key, requested, connection.source());
        TerminalNetworkStorage.checkAmount(inserted, requested);
        if (inserted > 0) player.containerMenu.setCarried(carried.copyWithCount(carried.getCount() - (int) inserted));
    }

    private static void extractCursor(ServerPlayer player, TerminalConnection connection, AEItemKey key, boolean half) {
        long amount = key.getMaxStackSize();
        if (half) {
            long available = connection.storage().extract(key, amount, Actionable.SIMULATE, connection.source());
            TerminalNetworkStorage.checkAmount(available, amount);
            amount = (available + 1) / 2;
        }
        if (amount == 0) return;
        long extracted = StorageHelper.poweredExtraction(connection.grid().getEnergyService(), connection.storage(), key, amount, connection.source());
        TerminalNetworkStorage.checkAmount(extracted, amount);
        if (extracted > 0) player.containerMenu.setCarried(key.toStack((int) extracted));
    }

    private static void extractSingle(ServerPlayer player, TerminalConnection connection, AEItemKey key) {
        ItemStack carried = player.containerMenu.getCarried();
        if (!carried.isEmpty() && (!key.matches(carried) || carried.getCount() >= carried.getMaxStackSize())) return;
        long extracted = StorageHelper.poweredExtraction(connection.grid().getEnergyService(), connection.storage(), key, 1, connection.source());
        TerminalNetworkStorage.checkAmount(extracted, 1);
        if (extracted > 0) player.containerMenu.setCarried(carried.isEmpty() ? key.toStack(1) : carried.copyWithCount(carried.getCount() + 1));
    }

    private static ObjectList<Slot> playerSlots(ServerPlayer player) {
        var hostSlots = new Int2ObjectOpenHashMap<Slot>();
        for (Slot slot : player.containerMenu.slots)
            if (TerminalNetworkStorage.isPlayerInventorySlot(player, slot)) hostSlots.putIfAbsent(slot.getContainerSlot(), slot);

        // The permanent inventory supplies main inventory before hotbar, including hosts that expose neither.
        // Prefer the host's actual slots where present so its placement and capacity rules remain authoritative.
        ObjectList<Slot> result = new ObjectArrayList<>();
        for (Slot slot : player.inventoryMenu.slots)
            if (TerminalNetworkStorage.isPlayerInventorySlot(player, slot)) result.add(hostSlots.getOrDefault(slot.getContainerSlot(), slot));
        return result;
    }

    private static boolean moveStackToPlayer(TerminalConnection connection, AEItemKey key, ObjectList<Slot> slots) {
        ItemStack sample = key.toStack();
        for (Slot slot : slots) {
            if (!slot.isActive() || !slot.mayPlace(sample)) continue;
            ItemStack existing = slot.getItem();
            if (!existing.isEmpty() && !key.matches(existing)) continue;
            int capacity = slot.getMaxStackSize(existing.isEmpty() ? sample : existing) - existing.getCount();
            if (capacity <= 0) continue;
            long extracted = StorageHelper.poweredExtraction(connection.grid().getEnergyService(), connection.storage(), key, capacity, connection.source());
            TerminalNetworkStorage.checkAmount(extracted, capacity);
            if (extracted == 0) return false;
            slot.setByPlayer(key.toStack(existing.getCount() + (int) extracted));
            return true;
        }
        return false;
    }
}
