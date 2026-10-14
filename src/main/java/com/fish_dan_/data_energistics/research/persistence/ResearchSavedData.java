package com.fish_dan_.data_energistics.research.persistence;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.research.progression.ResearchStationState;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import it.unimi.dsi.fastutil.objects.ObjectSets;

import java.util.UUID;

/** Overworld-owned identity bridge, unlock ledger and station journals. Access on the server thread only. */
public final class ResearchSavedData extends SavedData {

    private static final Factory<ResearchSavedData> FACTORY = new Factory<>(ResearchSavedData::new, ResearchSavedData::load);
    private final Object2ObjectMap<String, UUID> identities = new Object2ObjectLinkedOpenHashMap<>();
    private final Object2ObjectMap<UUID, ObjectSet<ResourceLocation>> unlocks = new Object2ObjectLinkedOpenHashMap<>();
    private final Object2ObjectMap<UUID, ResearchStationState> stations = new Object2ObjectLinkedOpenHashMap<>();
    private final Object2ObjectMap<UUID, CompoundTag> quarantined = new Object2ObjectLinkedOpenHashMap<>();

    public static ResearchSavedData get(MinecraftServer server) {
        if (!server.isSameThread()) throw new IllegalStateException("Research data requires the server thread");
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, "data_energistics_research");
    }

    public UUID subject(String identity, UUID stableId) {
        UUID existing = identities.get(identity);
        if (existing != null) return existing;
        identities.put(identity, stableId);
        setDirty();
        return stableId;
    }

    public ResearchStationState station(UUID id) {
        if (quarantined.containsKey(id)) throw new IllegalStateException("Research station journal quarantined: " + id);
        return stations.computeIfAbsent(id, unused -> {
            setDirty();
            return new ResearchStationState();
        });
    }

    public ObjectSet<ResourceLocation> unlocked(UUID subject) {
        return ObjectSets.unmodifiable(unlocks.getOrDefault(subject, ObjectSets.emptySet()));
    }

    public void unlock(Iterable<UUID> subjects, ResourceLocation node) {
        for (UUID subject : subjects) {
            if (unlocks.computeIfAbsent(subject, unused -> new ObjectOpenHashSet<>()).add(node)) setDirty();
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("version", 1);
        ListTag bridge = new ListTag();
        identities.forEach((identity, uuid) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString("identity", identity);
            entry.putUUID("uuid", uuid);
            bridge.add(entry);
        });
        tag.put("identities", bridge);
        ListTag ledger = new ListTag();
        unlocks.forEach((uuid, nodes) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("uuid", uuid);
            entry.put("nodes", ResearchNbt.ids(nodes));
            ledger.add(entry);
        });
        tag.put("unlocks", ledger);
        ListTag journals = new ListTag();
        stations.forEach((uuid, state) -> {
            CompoundTag entry = state.save(registries);
            entry.putUUID("uuid", uuid);
            journals.add(entry);
        });
        quarantined.values().forEach(entry -> journals.add(entry.copy()));
        tag.put("stations", journals);
        return tag;
    }

    private static ResearchSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        ResearchSavedData data = new ResearchSavedData();
        for (Tag value : tag.getList("identities", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) value;
            data.identities.put(entry.getString("identity"), entry.getUUID("uuid"));
        }
        for (Tag value : tag.getList("unlocks", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) value;
            data.unlocks.put(entry.getUUID("uuid"), ResearchNbt.readIds(entry, "nodes"));
        }
        for (Tag value : tag.getList("stations", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) value;
            UUID uuid = entry.getUUID("uuid");
            try {
                data.stations.put(uuid, ResearchStationState.load(entry, registries));
            } catch (RuntimeException error) {
                data.quarantined.put(uuid, entry.copy());
                Data_Energistics.LOGGER.error("Quarantined research station {} without discarding its journal", uuid, error);
            }
        }
        return data;
    }
}
