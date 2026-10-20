package com.fish_dan_.data_energistics.common.terminal.player.maintenance;

import com.fish_dan_.data_energistics.Data_Energistics;

import appeng.api.stacks.AEKey;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * Player-owned persistent list of automatic AE crafting thresholds.
 */
public final class TerminalMaintenanceState {

    public static final int MAX_ENTRIES = 256;

    private final ObjectList<TerminalMaintenanceEntry> entries = new ObjectArrayList<>();
    private final Runnable changed;

    public TerminalMaintenanceState(Runnable changed) {
        this.changed = changed;
    }

    public ObjectList<TerminalMaintenanceEntry> entries() {
        return new ObjectImmutableList<>(entries);
    }

    public TerminalMaintenanceEntry entry(int index) {
        return entries.get(index);
    }

    public int size() {
        return entries.size();
    }

    public boolean active() {
        for (TerminalMaintenanceEntry entry : entries)
            if (entry.enabled() && entry.resource() != null && entry.upper() > 0) return true;
        return false;
    }

    public void add() {
        if (entries.size() >= MAX_ENTRIES)
            throw new IllegalArgumentException("Terminal maintenance entry limit reached");
        entries.add(new TerminalMaintenanceEntry(UUID.randomUUID(), null, 0, 64, 64, false));
        changed.run();
    }

    public void remove(int index) {
        entries.remove(index);
        changed.run();
    }

    public void resource(int index, @Nullable AEKey value) {
        replace(index, entry(index).resource(value));
    }

    public void lower(int index, long value) {
        if (value > entry(index).upper())
            throw new IllegalArgumentException("Maintenance lower threshold exceeds upper threshold");
        replace(index, entry(index).lower(value));
    }

    public void upper(int index, long value) {
        if (value < entry(index).lower())
            throw new IllegalArgumentException("Maintenance upper threshold is below lower threshold");
        replace(index, entry(index).upper(value));
    }

    public void batch(int index, long value) {
        replace(index, entry(index).batch(value));
    }

    public void enabled(int index, boolean value) {
        replace(index, entry(index).enabled(value));
    }

    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        ListTag saved = new ListTag();
        for (TerminalMaintenanceEntry entry : entries) {
            CompoundTag value = new CompoundTag();
            value.putString("id", entry.id().toString());
            if (entry.resource() != null) value.put("resource", entry.resource().toTagGeneric(registries));
            value.putLong("lower", entry.lower());
            value.putLong("upper", entry.upper());
            value.putLong("batch", entry.batch());
            value.putBoolean("enabled", entry.enabled());
            saved.add(value);
        }
        tag.put("entries", saved);
        return tag;
    }

    public void load(CompoundTag tag, HolderLookup.Provider registries) {
        entries.clear();
        ListTag saved = tag.getList("entries", Tag.TAG_COMPOUND);
        for (int index = 0; index < Math.min(saved.size(), MAX_ENTRIES); index++) {
            CompoundTag value = saved.getCompound(index);
            try {
                UUID id = UUID.fromString(value.getString("id"));
                AEKey resource = value.contains("resource", Tag.TAG_COMPOUND) ? AEKey.fromTagGeneric(registries, value.getCompound("resource")) : null;
                long lower = Math.max(0, value.getLong("lower"));
                long upper = Math.max(0, value.getLong("upper"));
                long batch = Math.max(1, value.getLong("batch"));
                entries.add(new TerminalMaintenanceEntry(id, resource, lower, upper, batch, value.getBoolean("enabled")));
            } catch (IllegalArgumentException invalidEntry) {
                Data_Energistics.LOGGER.warn("Ignoring malformed player terminal maintenance entry at index {}", index, invalidEntry);
            }
        }
    }

    private void replace(int index, TerminalMaintenanceEntry value) {
        TerminalMaintenanceEntry previous = entries.set(index, value);
        if (previous.equals(value)) return;
        changed.run();
    }
}
