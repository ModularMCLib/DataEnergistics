package com.fish_dan_.data_energistics.world.packaged;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectList;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/** Dimension-local durable exclusion: another provider cannot spend inputs into an already owned machine. */
public final class PackagedMachineClaims extends SavedData {

    private static final Factory<PackagedMachineClaims> FACTORY = new Factory<>(PackagedMachineClaims::new, PackagedMachineClaims::load);
    private final Long2ObjectOpenHashMap<UUID> owners = new Long2ObjectOpenHashMap<>();

    public static PackagedMachineClaims get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, "data_energistics_packaged_claims");
    }

    public boolean available(BlockPos position) {
        return !this.owners.containsKey(position.asLong());
    }

    public @Nullable UUID owner(BlockPos position) {
        return this.owners.get(position.asLong());
    }

    public boolean acquire(BlockPos position, UUID operation) {
        UUID owner = this.owners.putIfAbsent(position.asLong(), operation);
        if (owner == null) setDirty();
        return owner == null || owner.equals(operation);
    }

    public boolean acquireAll(ObjectList<BlockPos> positions, UUID operation) {
        for (var position : positions) {
            var owner = owner(position);
            if (owner != null && !owner.equals(operation)) return false;
        }
        for (var position : positions) acquire(position, operation);
        return true;
    }

    public void releaseAll(ObjectList<BlockPos> positions, UUID operation) {
        for (var position : positions) release(position, operation);
    }

    public void release(BlockPos position, UUID operation) {
        if (!this.owners.remove(position.asLong(), operation)) {
            throw new IllegalStateException("Packaged operation does not own its machine");
        }
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        var entries = new ListTag();
        for (var entry : this.owners.long2ObjectEntrySet()) {
            var encoded = new CompoundTag();
            encoded.putLong("position", entry.getLongKey());
            encoded.putUUID("operation", entry.getValue());
            entries.add(encoded);
        }
        tag.put("claims", entries);
        return tag;
    }

    private static PackagedMachineClaims load(CompoundTag tag, HolderLookup.Provider registries) {
        var state = new PackagedMachineClaims();
        var entries = tag.getList("claims", Tag.TAG_COMPOUND);
        for (int index = 0; index < entries.size(); index++) {
            var entry = entries.getCompound(index);
            if (state.owners.putIfAbsent(entry.getLong("position"), entry.getUUID("operation")) != null) {
                throw new IllegalArgumentException("Duplicate packaged machine claim");
            }
        }
        return state;
    }
}
