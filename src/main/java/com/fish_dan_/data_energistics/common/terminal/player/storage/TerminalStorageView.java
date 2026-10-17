package com.fish_dan_.data_energistics.common.terminal.player.storage;

import com.fish_dan_.data_energistics.common.terminal.player.connection.TerminalConnection;

import appeng.api.networking.IGrid;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import org.jspecify.annotations.Nullable;

/** Publishes a bounded resource-index delta; localized searching and JECh matching belong to the client. */
public final class TerminalStorageView {

    private static final int MAX_CHANGES = 512;
    private final Object2ObjectMap<AEKey, Entry> published = new Object2ObjectLinkedOpenHashMap<>();
    private final ObjectLinkedOpenHashSet<AEKey> changedKeys = new ObjectLinkedOpenHashSet<>();
    private @Nullable IGrid grid;
    private boolean initialized;
    private long revision;

    public void snapshot(CompoundTag snapshot, @Nullable TerminalConnection connection, HolderLookup.Provider registries) {
        IGrid nextGrid = connection == null ? null : connection.grid();
        boolean reset = !initialized || grid != nextGrid;
        if (reset) {
            initialized = true;
            grid = nextGrid;
            published.clear();
            changedKeys.clear();
        }
        Object2ObjectMap<AEKey, Entry> current = new Object2ObjectLinkedOpenHashMap<>();
        if (grid != null) {
            for (var entry : grid.getStorageService().getCachedInventory()) {
                if (entry.getLongValue() > 0) current.put(entry.getKey(), new Entry(entry.getLongValue(), false));
            }
            for (AEKey key : grid.getCraftingService().getCraftables(value -> true)) {
                long amount = current.containsKey(key) ? current.get(key).amount() : 0;
                current.put(key, new Entry(amount, true));
            }
        }
        // Keep unsent keys in arrival order. Frequently changing entries must not starve the rest of a large index.
        for (AEKey key : published.keySet()) if (!current.containsKey(key)) changedKeys.add(key);
        for (var entry : current.object2ObjectEntrySet()) {
            if (!entry.getValue().equals(published.get(entry.getKey()))) changedKeys.add(entry.getKey());
        }
        int changes = 0;
        ListTag removed = new ListTag();
        ListTag updates = new ListTag();
        while (!changedKeys.isEmpty() && changes < MAX_CHANGES) {
            AEKey key = changedKeys.removeFirst();
            if (current.containsKey(key)) {
                Entry entry = current.get(key);
                if (entry.equals(published.get(key))) continue;
                CompoundTag tag = new CompoundTag();
                tag.put("stack", GenericStack.writeTag(registries, new GenericStack(key, entry.amount())));
                tag.putBoolean("craftable", entry.craftable());
                updates.add(tag);
                published.put(key, entry);
            } else {
                if (!published.containsKey(key)) continue;
                removed.add(key.toTagGeneric(registries));
                published.remove(key);
            }
            changes++;
        }
        if (reset || changes > 0) revision++;
        snapshot.put("storage", updates);
        snapshot.put("storage_removed", removed);
        snapshot.putLong("storage_revision", revision);
        snapshot.putBoolean("storage_reset", reset);
        snapshot.putBoolean("storage_pending", !changedKeys.isEmpty());
        snapshot.putInt("storage_total", current.size());
    }

    private record Entry(long amount, boolean craftable) {
    }
}
