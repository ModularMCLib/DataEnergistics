package com.fish_dan_.data_energistics.integration.magic.naturesaura.packaged.storage;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

import java.util.UUID;

/** Native consumption and queued outputs survive destruction of the ritual's block entity. */
public final class NatureRitualLedger extends SavedData {

    private static final Factory<NatureRitualLedger> FACTORY = new Factory<>(NatureRitualLedger::new, NatureRitualLedger::load);
    private final Object2ObjectOpenHashMap<UUID, CompoundTag> operations = new Object2ObjectOpenHashMap<>();

    public static NatureRitualLedger get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, "data_energistics_nature_rituals");
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

    /** Called after the operation has taken ownership of all remaining ledger assets. */
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

    private static NatureRitualLedger load(CompoundTag tag, HolderLookup.Provider registries) {
        var ledger = new NatureRitualLedger();
        for (var encoded : tag.getList("operations", Tag.TAG_COMPOUND)) {
            var entry = (CompoundTag) encoded;
            if (!entry.hasUUID("operation") || !entry.contains("data", Tag.TAG_COMPOUND)) throw new IllegalArgumentException("Invalid nature ritual ledger entry");
            if (ledger.operations.putIfAbsent(entry.getUUID("operation"), entry.getCompound("data").copy()) != null) throw new IllegalArgumentException("Duplicate nature ritual operation");
        }
        return ledger;
    }
}
