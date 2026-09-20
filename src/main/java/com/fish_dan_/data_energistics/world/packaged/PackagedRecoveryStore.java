package com.fish_dan_.data_energistics.world.packaged;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/** Server-thread escrow for dismantled providers. Item copies share one redeemable receipt, never the assets. */
public final class PackagedRecoveryStore extends SavedData {

    private static final Factory<PackagedRecoveryStore> FACTORY = new Factory<>(PackagedRecoveryStore::new, PackagedRecoveryStore::load);
    private final Object2ObjectLinkedOpenHashMap<UUID, CompoundTag> entries = new Object2ObjectLinkedOpenHashMap<>();

    public static PackagedRecoveryStore get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, "data_energistics_packaged_recovery");
    }

    public void deposit(UUID id, BlockPos origin, String kind, CompoundTag state, ListTag returns) {
        var payload = new CompoundTag();
        payload.putLong("origin", origin.asLong());
        payload.putString("kind", kind);
        payload.put("state", state.copy());
        payload.put("returns", returns.copy());
        if (this.entries.putIfAbsent(id, payload) != null) throw new IllegalStateException("Duplicate packaged recovery receipt");
        setDirty();
    }

    /** A detached copy permits full validation before redemption or host mutation. */
    public @Nullable CompoundTag inspect(UUID id, BlockPos origin, String kind) {
        var payload = this.entries.get(id);
        if (payload == null || payload.getLong("origin") != origin.asLong() || !payload.getString("kind").equals(kind)) return null;
        return payload.copy();
    }

    public void redeem(UUID id) {
        if (this.entries.remove(id) == null) throw new IllegalStateException("Packaged recovery receipt has already been redeemed");
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        var saved = new ListTag();
        this.entries.forEach((id, payload) -> {
            var entry = payload.copy();
            entry.putUUID("id", id);
            saved.add(entry);
        });
        tag.put("receipts", saved);
        return tag;
    }

    static PackagedRecoveryStore load(CompoundTag tag, HolderLookup.Provider registries) {
        var result = new PackagedRecoveryStore();
        if (!(tag.get("receipts") instanceof ListTag saved) || !saved.isEmpty() && saved.getElementType() != Tag.TAG_COMPOUND) {
            throw new IllegalArgumentException("Malformed packaged recovery receipt list");
        }
        for (int index = 0; index < saved.size(); index++) {
            var entry = saved.getCompound(index);
            if (!entry.hasUUID("id") || !entry.contains("origin", Tag.TAG_LONG) || entry.getString("kind").isEmpty() ||
                    !entry.contains("state", Tag.TAG_COMPOUND) || !(entry.get("returns") instanceof ListTag returns) ||
                    !returns.isEmpty() && returns.getElementType() != Tag.TAG_COMPOUND) {
                throw new IllegalArgumentException("Malformed packaged recovery escrow");
            }
            var payload = entry.copy();
            var id = payload.getUUID("id");
            payload.remove("id");
            if (result.entries.putIfAbsent(id, payload) != null) throw new IllegalArgumentException("Duplicate saved packaged recovery receipt");
        }
        return result;
    }
}
