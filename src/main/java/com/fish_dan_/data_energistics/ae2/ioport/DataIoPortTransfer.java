package com.fish_dan_.data_energistics.ae2.ioport;

import appeng.api.config.Actionable;
import appeng.api.config.FullnessMode;
import appeng.api.config.OperationMode;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.MEStorage;
import appeng.api.storage.StorageCells;
import appeng.api.storage.cells.CellState;
import appeng.api.storage.cells.StorageCell;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;

/** Executes bounded storage-cell transfers without multiplying long quotas across parallel rounds. */
public final class DataIoPortTransfer {

    private final InternalInventory input;
    private final InternalInventory output;
    private final IActionSource actionSource;
    private final int[] keyCursors;
    private int cellCursor;
    private @Nullable GenericStack pending;

    public DataIoPortTransfer(InternalInventory input, InternalInventory output, IActionSource actionSource) {
        if (input.size() == 0 || input.size() != output.size()) {
            throw new IllegalArgumentException("Cell input and output inventories must have the same nonzero size");
        }
        this.input = input;
        this.output = output;
        this.actionSource = actionSource;
        this.keyCursors = new int[input.size()];
    }

    /** Runs on the owning server thread; the caller persists inventory and cursor changes after each call. */
    public boolean transfer(MEStorage network, OperationMode operation, FullnessMode fullness,
                            int speedCards, int energyCards) {
        long quota = quota(speedCards);
        int rounds = rounds(energyCards);
        boolean changed = false;
        for (int round = 0; round < rounds; round++) {
            long recovered = recoverPending(network, quota);
            long remaining = quota - recovered;
            boolean roundChanged = recovered > 0;
            changed |= roundChanged;
            if (pending != null) {
                if (!roundChanged) break;
                continue;
            }
            int start = cellCursor;
            for (int offset = 0; offset < input.size() && remaining > 0; offset++) {
                int slot = (start + offset) % input.size();
                cellCursor = (slot + 1) % input.size();
                ItemStack stack = input.getStackInSlot(slot);
                if (stack.isEmpty()) continue;
                StorageCell cell = StorageCells.getCellInventory(stack, null);
                if (cell == null) {
                    roundChanged |= moveToOutput(slot);
                    continue;
                }
                boolean complete;
                try {
                    MEStorage from = operation == OperationMode.EMPTY ? cell : network;
                    MEStorage to = operation == OperationMode.EMPTY ? network : cell;
                    long before = remaining;
                    remaining = transferCell(slot, from, to, remaining);
                    roundChanged |= remaining < before;
                    if (pending != null) return changed || roundChanged;
                    complete = switch (fullness) {
                        case EMPTY -> cell.getStatus() == CellState.EMPTY;
                        case FULL -> cell.getStatus() == CellState.FULL;
                        case HALF -> remaining > 0 && !canTransfer(from, to);
                    };
                } finally {
                    cell.persist();
                }
                if (complete) roundChanged |= moveToOutput(slot);
            }
            changed |= roundChanged;
            if (!roundChanged) break;
        }
        return changed;
    }

    private long transferCell(int slot, MEStorage from, MEStorage to, long remaining) {
        var available = from.getAvailableStacks();
        var keys = new ObjectArrayList<AEKey>();
        for (var entry : available) {
            if (entry.getLongValue() > 0) keys.add(entry.getKey());
        }
        if (keys.isEmpty()) return remaining;
        int start = keyCursors[slot] % keys.size();
        for (int offset = 0; offset < keys.size() && remaining > 0; offset++) {
            int index = (start + offset) % keys.size();
            keyCursors[slot] = (index + 1) % keys.size();
            AEKey key = keys.get(index);
            // Availability lists may advertise an int sentinel for unlimited storage. Query the actual contract.
            long requested = checked(from.extract(key, remaining, Actionable.SIMULATE, actionSource), remaining);
            if (requested == 0) continue;
            long accepted = checked(to.insert(key, requested, Actionable.SIMULATE, actionSource), requested);
            if (accepted == 0) continue;
            long extracted = checked(from.extract(key, accepted, Actionable.MODULATE, actionSource), accepted);
            if (extracted == 0) continue;

            // Once extracted, this port owns the remainder until the destination or source accepts it.
            pending = new GenericStack(key, extracted);
            long inserted = checked(to.insert(key, extracted, Actionable.MODULATE, actionSource), extracted);
            setPending(key, extracted - inserted);
            if (pending != null) {
                long leftover = pending.amount();
                long returned = checked(from.insert(key, leftover, Actionable.MODULATE, actionSource), leftover);
                setPending(key, leftover - returned);
            }
            remaining -= inserted;
            if (pending != null) break;
        }
        return remaining;
    }

    private boolean canTransfer(MEStorage from, MEStorage to) {
        for (var entry : from.getAvailableStacks()) {
            if (entry.getLongValue() <= 0) continue;
            AEKey key = entry.getKey();
            long extracted = checked(from.extract(key, Long.MAX_VALUE, Actionable.SIMULATE, actionSource), Long.MAX_VALUE);
            if (extracted > 0 && checked(to.insert(key, extracted, Actionable.SIMULATE, actionSource), extracted) > 0) {
                return true;
            }
        }
        return false;
    }

    private boolean moveToOutput(int slot) {
        for (int target = 0; target < output.size(); target++) {
            if (output.getStackInSlot(target).isEmpty()) {
                output.setItemDirect(target, input.getStackInSlot(slot));
                input.setItemDirect(slot, ItemStack.EMPTY);
                keyCursors[slot] = 0;
                return true;
            }
        }
        return false;
    }

    private long recoverPending(MEStorage network, long quota) {
        if (pending == null) return 0;
        long amount = pending.amount();
        long requested = Math.min(amount, quota);
        long inserted = checked(network.insert(pending.what(), requested, Actionable.MODULATE, actionSource), requested);
        setPending(pending.what(), amount - inserted);
        return inserted;
    }

    private void setPending(AEKey key, long amount) {
        pending = amount == 0 ? null : new GenericStack(key, amount);
    }

    private static long checked(long actual, long requested) {
        if (actual < 0 || actual > requested) {
            throw new IllegalStateException("Storage returned " + actual + " for a request of " + requested);
        }
        return actual;
    }

    public boolean hasPending() {
        return pending != null;
    }

    public void resetCell(int slot) {
        keyCursors[slot] = 0;
    }

    public CompoundTag save(HolderLookup.Provider registries) {
        var tag = new CompoundTag();
        tag.putInt("cell_cursor", cellCursor);
        tag.putIntArray("key_cursors", keyCursors);
        tag.put("pending", GenericStack.writeTag(registries, pending));
        return tag;
    }

    public void load(CompoundTag tag, HolderLookup.Provider registries) {
        cellCursor = Math.floorMod(tag.getInt("cell_cursor"), input.size());
        Arrays.fill(keyCursors, 0);
        int[] storedCursors = tag.getIntArray("key_cursors");
        for (int i = 0; i < Math.min(keyCursors.length, storedCursors.length); i++) {
            keyCursors[i] = Math.max(0, storedCursors[i]);
        }
        pending = GenericStack.readTag(registries, tag.getCompound("pending"));
        if (pending != null && pending.amount() <= 0) {
            throw new IllegalArgumentException("Pending I/O resources must have a positive amount");
        }
    }

    public void clear() {
        pending = null;
        cellCursor = 0;
        Arrays.fill(keyCursors, 0);
    }

    public static long quota(int speedCards) {
        if (speedCards < 0 || speedCards > 4) throw new IllegalArgumentException("Invalid speed card count: " + speedCards);
        return speedCards == 0 ? 256L : (Long.MAX_VALUE / 4L) * speedCards + (Long.MAX_VALUE % 4L) * speedCards / 4L;
    }

    public static int rounds(int energyCards) {
        if (energyCards < 0 || energyCards > 4) throw new IllegalArgumentException("Invalid energy card count: " + energyCards);
        return 1 << energyCards;
    }
}
