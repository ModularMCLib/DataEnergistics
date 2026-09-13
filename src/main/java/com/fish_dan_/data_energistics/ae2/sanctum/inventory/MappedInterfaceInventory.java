package com.fish_dan_.data_energistics.ae2.sanctum.inventory;

import com.fish_dan_.data_energistics.ae2.sanctum.DataSanctumInterfaceInventory;

import appeng.api.config.Actionable;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyTypes;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.AEKeySlotFilter;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.function.IntSupplier;

/**
 * A slot view over local stock and explicitly mapped AE keys. Mapping never transfers or serializes
 * network contents. The backing array owns only real items, including stock awaiting return.
 * All calls run on the owning server thread; reentry through a storage bus cannot expose this view again.
 */
@NullMarked
public final class MappedInterfaceInventory extends DataSanctumInterfaceInventory {

    private final DataSanctumInterfaceInventory config;
    private final IManagedGridNode node;
    private final IActionSource source;
    private boolean accessingNetwork;

    public MappedInterfaceInventory(DataSanctumInterfaceInventory config, IManagedGridNode node,
                                    IActionSource source, AEKeySlotFilter filter, Runnable listener,
                                    IntSupplier capacityCards) {
        super(AEKeyTypes.getAll(), filter, Mode.STORAGE, config.size(), listener, capacityCards);
        this.config = config;
        this.node = node;
        this.source = source;
    }

    public boolean isMapped(int slot) {
        return config.isSlotUnlocked(slot) && config.isUnlimitedSlot(slot) && config.getKey(slot) != null;
    }

    public @Nullable GenericStack physicalStack(int slot) {
        return this.stacks[slot];
    }

    public void setPhysicalStack(int slot, @Nullable GenericStack stack) {
        super.setStack(slot, stack);
    }

    @Override
    public @Nullable GenericStack getStack(int slot) {
        if (!isMapped(slot)) {
            return physicalStack(slot);
        }
        AEKey key = config.getKey(slot);
        return new GenericStack(key, networkExtract(key, Long.MAX_VALUE, Actionable.SIMULATE));
    }

    @Override
    public void setStack(int slot, @Nullable GenericStack stack) {
        // Virtual snapshots are never materialized by a slot adapter.
        if (!isMapped(slot)) {
            super.setStack(slot, stack);
        }
    }

    @Override
    public long insert(int slot, AEKey key, long amount, Actionable mode) {
        if (amount < 0) throw new IllegalArgumentException("amount >= 0");
        if (accessingNetwork) return 0;
        if (isMapped(slot)) {
            return key.equals(config.getKey(slot)) ? networkInsert(key, amount, mode) : 0;
        }
        return super.insert(slot, key, amount, mode);
    }

    @Override
    public long extract(int slot, AEKey key, long amount, Actionable mode) {
        if (amount < 0) throw new IllegalArgumentException("amount >= 0");
        if (accessingNetwork) return 0;
        if (isMapped(slot)) {
            return key.equals(config.getKey(slot)) ? networkExtract(key, amount, mode) : 0;
        }
        return super.extract(slot, key, amount, mode);
    }

    public long networkExtract(AEKey key, long amount, Actionable mode) {
        var grid = node.getGrid();
        if (accessingNetwork || !node.isActive() || grid == null || amount == 0) return 0;
        accessingNetwork = true;
        try {
            return grid.getStorageService().getInventory().extract(key, amount, mode, source);
        } finally {
            accessingNetwork = false;
        }
    }

    public long networkInsert(AEKey key, long amount, Actionable mode) {
        var grid = node.getGrid();
        if (accessingNetwork || !node.isActive() || grid == null || amount == 0) return 0;
        accessingNetwork = true;
        try {
            return grid.getStorageService().getInventory().insert(key, amount, mode, source);
        } finally {
            accessingNetwork = false;
        }
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        if (accessingNetwork) return;
        var mappedKeys = new HashSet<AEKey>();
        for (int slot = 0; slot < size(); slot++) {
            if (isMapped(slot)) {
                AEKey key = config.getKey(slot);
                if (mappedKeys.add(key)) {
                    long amount = networkExtract(key, Long.MAX_VALUE, Actionable.SIMULATE);
                    // Two mapped slots referencing the same key share one network amount.
                    long existing = out.get(key);
                    out.add(key, Math.min(amount, Long.MAX_VALUE - existing));
                }
            } else {
                var stack = physicalStack(slot);
                if (stack != null) {
                    long existing = out.get(stack.what());
                    out.add(stack.what(), Math.min(stack.amount(), Long.MAX_VALUE - existing));
                }
            }
        }
    }
}
