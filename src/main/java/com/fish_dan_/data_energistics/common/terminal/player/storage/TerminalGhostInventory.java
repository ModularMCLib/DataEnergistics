package com.fish_dan_.data_energistics.common.terminal.player.storage;

import appeng.api.stacks.GenericStack;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.ListTag;

import org.jspecify.annotations.Nullable;

/**
 * Configuration resources only: changing a ghost never creates or consumes a physical item.
 */
public final class TerminalGhostInventory {

    private final @Nullable GenericStack[] values;
    private final Runnable changed;

    public TerminalGhostInventory(int size, Runnable changed) {
        if (size <= 0 || size > 81) throw new IllegalArgumentException("Invalid ghost inventory size");
        this.values = new GenericStack[size];
        this.changed = changed;
    }

    public int size() {
        return values.length;
    }

    public @Nullable GenericStack get(int index) {
        return values[index];
    }

    public void set(int index, @Nullable GenericStack value) {
        if (value != null && value.amount() <= 0) throw new IllegalArgumentException("Ghost amounts must be positive");
        values[index] = value;
        changed.run();
    }

    public void copyFrom(TerminalGhostInventory source) {
        if (source.size() != size()) throw new IllegalArgumentException("Ghost inventory sizes differ");
        System.arraycopy(source.values, 0, values, 0, values.length);
        changed.run();
    }

    public void swap(TerminalGhostInventory other) {
        if (other.size() != size()) throw new IllegalArgumentException("Ghost inventory sizes differ");
        for (int index = 0; index < values.length; index++) {
            GenericStack previous = values[index];
            values[index] = other.values[index];
            other.values[index] = previous;
        }
        changed.run();
        other.changed.run();
    }

    public ListTag save(HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (GenericStack value : values) list.add(GenericStack.writeTag(registries, value));
        return list;
    }

    public void load(ListTag list, HolderLookup.Provider registries) {
        for (int index = 0; index < values.length; index++)
            values[index] = GenericStack.readTag(registries, list.getCompound(index));
    }
}
