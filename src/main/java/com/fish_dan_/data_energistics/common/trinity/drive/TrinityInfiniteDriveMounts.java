package com.fish_dan_.data_energistics.common.trinity.drive;

import appeng.api.storage.IStorageMounts;
import appeng.api.storage.StorageCells;
import appeng.api.storage.cells.ISaveProvider;
import appeng.api.storage.cells.StorageCell;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.List;

/** Server-thread cell inventories shared by successive mounts of the elected information exchange depot. */
public final class TrinityInfiniteDriveMounts implements ISaveProvider {

    private final TrinityInfiniteDriveInventory inventory;
    private final Runnable markChanged;
    private final List<StorageCell> cells = new ObjectArrayList<>();
    private int activeSlots = -1;

    public TrinityInfiniteDriveMounts(TrinityInfiniteDriveInventory inventory, Runnable markChanged) {
        this.inventory = inventory;
        this.markChanged = markChanged;
    }

    /** Flushes cell contents before a slot replacement or capacity change discards the opened inventories. */
    public void invalidate() {
        this.cells.forEach(StorageCell::persist);
        this.cells.clear();
        this.activeSlots = -1;
    }

    /** Adds separate, original cell inventories beside the host's internal storage, preserving addon capabilities. */
    public void mount(IStorageMounts mounts, int availableSlots, int priority) {
        if (this.activeSlots != availableSlots) {
            invalidate();
            for (int slot = 0; slot < availableSlots; slot++) {
                StorageCell cell = StorageCells.getCellInventory(this.inventory.getItem(slot), this);
                if (cell != null) {
                    this.cells.add(cell);
                }
            }
            this.activeSlots = availableSlots;
        }
        for (StorageCell cell : this.cells) {
            mounts.mount(cell, priority);
        }
    }

    /** Persists network writes immediately so taking a cell out or switching depots cannot lose its changes. */
    @Override
    public void saveChanges() {
        this.cells.forEach(StorageCell::persist);
        this.markChanged.run();
    }
}
