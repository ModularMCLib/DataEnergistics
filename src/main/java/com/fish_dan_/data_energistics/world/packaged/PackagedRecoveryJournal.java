package com.fish_dan_.data_energistics.world.packaged;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

import java.util.UUID;

/**
 * Dimension-local recovery evidence for native packaged operations, keyed by operation UUID.
 * Native callbacks can write while the provider is unloaded or its recovery voucher is outstanding.
 * Server-thread only; adapters define the payload and claims release its lifetime.
 */
public final class PackagedRecoveryJournal extends SavedData {

    private static final Factory<PackagedRecoveryJournal> FACTORY = new Factory<>(PackagedRecoveryJournal::new, PackagedRecoveryJournal::load);
    private final Object2ObjectOpenHashMap<UUID, CompoundTag> operations = new Object2ObjectOpenHashMap<>();

    public static PackagedRecoveryJournal get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, "data_energistics_packaged_recovery_journal");
    }

    /** Returns an isolated server-thread snapshot; changes become durable only through write. */
    public CompoundTag read(UUID operation) {
        var data = this.operations.get(operation);
        return data == null ? new CompoundTag() : data.copy();
    }

    public void write(UUID operation, CompoundTag data) {
        if (data.equals(this.operations.get(operation))) return;
        this.operations.put(operation, data.copy());
        setDirty();
    }

    /** Removes evidence after all recoverable assets have transferred to the task or its receiver. */
    public void release(UUID operation) {
        if (this.operations.remove(operation) != null) setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        var entries = new ListTag();
        this.operations.forEach((operation, data) -> {
            var entry = new CompoundTag();
            entry.putUUID("operation", operation);
            entry.put("data", data.copy());
            entries.add(entry);
        });
        tag.put("operations", entries);
        return tag;
    }

    private static PackagedRecoveryJournal load(CompoundTag tag, HolderLookup.Provider registries) {
        var ledger = new PackagedRecoveryJournal();
        for (var encoded : tag.getList("operations", Tag.TAG_COMPOUND)) {
            var entry = (CompoundTag) encoded;
            if (!entry.hasUUID("operation") || !entry.contains("data", Tag.TAG_COMPOUND)) throw new IllegalArgumentException("Invalid packaged recovery journal entry");
            if (ledger.operations.putIfAbsent(entry.getUUID("operation"), entry.getCompound("data").copy()) != null) throw new IllegalArgumentException("Duplicate packaged recovery operation");
        }
        return ledger;
    }
}
