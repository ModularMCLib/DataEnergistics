package com.fish_dan_.data_energistics.world.packaged;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/** Dimension-local durable exclusion: another provider cannot spend inputs into an already owned machine. */
@EventBusSubscriber(modid = Data_Energistics.MODID)
public final class PackagedMachineClaims extends SavedData {

    private static final Factory<PackagedMachineClaims> FACTORY = new Factory<>(PackagedMachineClaims::new, PackagedMachineClaims::load);
    private final Long2ObjectOpenHashMap<UUID> owners = new Long2ObjectOpenHashMap<>();
    private final ObjectSet<UUID> removedStructures = new ObjectOpenHashSet<>();

    public static PackagedMachineClaims get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, "data_energistics_packaged_claims");
    }

    /** Repairs old saves with claims at already removed blocks without loading their chunks. */
    @SubscribeEvent
    public static void clearMissingBlocks(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.getGameTime() % 20 != 0) return;
        var claims = get(level);
        var missing = new LongArrayList();
        for (long packed : claims.owners.keySet()) {
            BlockPos position = BlockPos.of(packed);
            if (level.isLoaded(position) && level.getBlockState(position).isAir()) missing.add(packed);
        }
        for (long packed : missing) claims.blockReplaced(BlockPos.of(packed));
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
        if (this.removedStructures.contains(operation)) return false;
        for (var position : positions) {
            var owner = owner(position);
            if (owner != null && !owner.equals(operation)) return false;
        }
        for (var position : positions) acquire(position, operation);
        return true;
    }

    /** Called after a physical block replacement, never for chunk unload or a property-only state change. */
    public void blockReplaced(BlockPos position) {
        UUID operation = this.owners.get(position.asLong());
        if (operation == null) return;
        this.removedStructures.add(operation);
        this.owners.values().removeIf(operation::equals);
        setDirty();
    }

    /** Keeps old and escrowed tasks from reacquiring parts of a newly placed structure. */
    public boolean structureRemoved(UUID operation) {
        return this.removedStructures.contains(operation);
    }

    /** Called only once the retired operation has returned every resource still held by its provider. */
    public void acknowledgeRemoval(UUID operation) {
        if (this.removedStructures.remove(operation)) setDirty();
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
            if (state.removedStructures.contains(entry.getUUID("operation"))) continue;
            if (state.owners.putIfAbsent(entry.getLong("position"), entry.getUUID("operation")) != null) {
                throw new IllegalArgumentException("Duplicate packaged machine claim");
            }
        }
        return state;
    }
}
