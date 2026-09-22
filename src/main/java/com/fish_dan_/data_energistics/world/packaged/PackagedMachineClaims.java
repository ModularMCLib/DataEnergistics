package com.fish_dan_.data_energistics.world.packaged;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/** Dimension-local durable exclusion: another provider cannot spend inputs into an already owned machine. */
public final class PackagedMachineClaims extends SavedData {

    private static final Factory<PackagedMachineClaims> FACTORY = new Factory<>(PackagedMachineClaims::new, PackagedMachineClaims::load);
    private final Long2ObjectOpenHashMap<UUID> owners = new Long2ObjectOpenHashMap<>();
    private final LongOpenHashSet changingPositions = new LongOpenHashSet();
    private final ObjectSet<UUID> removedStructures = new ObjectOpenHashSet<>();
    private @Nullable UUID nativeMutation;

    public static PackagedMachineClaims get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, "data_energistics_packaged_claims");
    }

    public boolean available(BlockPos position) {
        return !this.owners.containsKey(position.asLong());
    }

    public @Nullable UUID owner(BlockPos position) {
        return this.owners.get(position.asLong());
    }

    /** Resolves a nearby claimed machine for native drops spawned outside its block tick callback. */
    public @Nullable UUID ownerNear(BlockPos position, int radius) {
        double best = Double.POSITIVE_INFINITY;
        UUID result = null;
        for (var entry : this.owners.long2ObjectEntrySet()) {
            BlockPos claimed = BlockPos.of(entry.getLongKey());
            double distance = claimed.distSqr(position);
            if (distance <= (double) radius * radius && distance < best) {
                best = distance;
                result = entry.getValue();
            }
        }
        return result;
    }

    public boolean acquire(BlockPos position, UUID operation) {
        if (this.removedStructures.contains(operation)) return false;
        UUID owner = this.owners.putIfAbsent(position.asLong(), operation);
        if (owner == null) setDirty();
        return owner == null || owner.equals(operation);
    }

    public boolean acquireAll(ObjectList<BlockPos> positions, UUID operation) {
        return acquireAll(positions, operation, new long[0]);
    }

    /** Reserves consumable structure cells without interpreting native replacement as dismantling. */
    public boolean acquireAll(ObjectList<BlockPos> positions, UUID operation, long[] changing) {
        if (this.removedStructures.contains(operation)) return false;
        for (var position : positions) {
            var owner = owner(position);
            if (owner != null && !owner.equals(operation)) return false;
        }
        for (long position : changing) {
            if (positions.stream().noneMatch(candidate -> candidate.asLong() == position)) throw new IllegalArgumentException("Unreserved changing position");
        }
        for (var position : positions) acquire(position, operation);
        for (long position : changing) {
            if (this.changingPositions.add(position)) setDirty();
        }
        return true;
    }

    /** Marks cells whose native lifecycle includes placement, growth, and consumption. */
    public void markChanging(ObjectList<BlockPos> positions, UUID operation) {
        for (var position : positions) {
            if (!operation.equals(this.owners.get(position.asLong()))) throw new IllegalArgumentException("Unreserved changing position");
            if (this.changingPositions.add(position.asLong())) setDirty();
        }
    }

    /** Allows a synchronous native growth call to change the reserved soil without dismantling its anchor. */
    public void nativeChange(UUID operation, Runnable action) {
        UUID previous = this.nativeMutation;
        this.nativeMutation = operation;
        try {
            action.run();
        } finally {
            this.nativeMutation = previous;
        }
    }

    /** Called after a physical block replacement, never for chunk unload or a property-only state change. */
    public void blockReplaced(BlockPos position) {
        if (this.changingPositions.contains(position.asLong())) return;
        UUID operation = this.owners.get(position.asLong());
        if (operation == null) return;
        if (operation.equals(this.nativeMutation)) return;
        retireOperation(operation);
    }

    /** Stops a detached provider's work from resuming before physical recovery has finished. */
    public void retireOperation(UUID operation) {
        if (this.removedStructures.add(operation)) setDirty();
    }

    /** Keeps old and escrowed tasks from reacquiring parts of a newly placed structure. */
    public boolean structureRemoved(UUID operation) {
        return this.removedStructures.contains(operation);
    }

    /** Called only once the retired operation has returned every resource still held by its provider. */
    public void acknowledgeRemoval(UUID operation) {
        if (this.removedStructures.remove(operation)) {
            this.owners.long2ObjectEntrySet().removeIf(entry -> {
                if (!operation.equals(entry.getValue())) return false;
                this.changingPositions.remove(entry.getLongKey());
                return true;
            });
            setDirty();
        }
    }

    public void releaseAll(ObjectList<BlockPos> positions, UUID operation) {
        for (var position : positions) release(position, operation);
    }

    public void release(BlockPos position, UUID operation) {
        if (!this.owners.remove(position.asLong(), operation)) {
            throw new IllegalStateException("Packaged operation does not own its machine");
        }
        this.changingPositions.remove(position.asLong());
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
        tag.putLongArray("changing_positions", this.changingPositions.toLongArray());
        var removed = new ListTag();
        for (UUID operation : this.removedStructures) {
            var entry = new CompoundTag();
            entry.putUUID("operation", operation);
            removed.add(entry);
        }
        tag.put("removed_structures", removed);
        return tag;
    }

    private static PackagedMachineClaims load(CompoundTag tag, HolderLookup.Provider registries) {
        var state = new PackagedMachineClaims();
        var removed = tag.getList("removed_structures", Tag.TAG_COMPOUND);
        for (int index = 0; index < removed.size(); index++) {
            state.removedStructures.add(removed.getCompound(index).getUUID("operation"));
        }
        var entries = tag.getList("claims", Tag.TAG_COMPOUND);
        for (int index = 0; index < entries.size(); index++) {
            var entry = entries.getCompound(index);
            if (state.owners.putIfAbsent(entry.getLong("position"), entry.getUUID("operation")) != null) {
                throw new IllegalArgumentException("Duplicate packaged machine claim");
            }
        }
        for (long position : tag.getLongArray("changing_positions")) {
            if (state.owners.containsKey(position)) state.changingPositions.add(position);
        }
        return state;
    }
}
