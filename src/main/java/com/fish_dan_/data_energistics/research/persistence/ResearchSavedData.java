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
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import it.unimi.dsi.fastutil.objects.ObjectSets;
import org.jspecify.annotations.Nullable;

import java.util.UUID;
import java.util.function.Consumer;

/** Overworld-owned identity bridge, unlock ledger and station journals. Access on the server thread only. */
public final class ResearchSavedData extends SavedData {

    private static final Factory<ResearchSavedData> FACTORY = new Factory<>(ResearchSavedData::new, ResearchSavedData::load);
    private final Object2ObjectMap<String, UUID> identities = new Object2ObjectLinkedOpenHashMap<>();
    private final Object2ObjectMap<UUID, ObjectSet<ResourceLocation>> unlocks = new Object2ObjectLinkedOpenHashMap<>();
    private final Object2ObjectMap<UUID, ResearchStationState> stations = new Object2ObjectLinkedOpenHashMap<>();
    private final Object2ObjectMap<UUID, CompoundTag> quarantined = new Object2ObjectLinkedOpenHashMap<>();
    private final ObjectList<Tag> quarantinedEntries = new ObjectArrayList<>();

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
        ListTag rejected = new ListTag();
        // Wrap opaque tags so malformed list entries of any NBT type remain serializable together.
        for (Tag entry : quarantinedEntries) {
            CompoundTag wrapper = new CompoundTag();
            wrapper.put("entry", entry.copy());
            rejected.add(wrapper);
        }
        tag.put("quarantined_entries", rejected);
        return tag;
    }

    private static ResearchSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        ResearchSavedData data = new ResearchSavedData();
        for (Tag value : data.entries(tag, "quarantined_entries")) {
            if (value instanceof CompoundTag wrapper && wrapper.contains("entry")) {
                data.quarantinedEntries.add(wrapper.get("entry").copy());
            } else {
                data.quarantine("quarantined_entries", value, new IllegalArgumentException("Invalid quarantine wrapper"));
            }
        }
        for (Tag value : data.entries(tag, "identities")) {
            data.readEntry("identities", value, entry -> {
                String identity = entry.getString("identity");
                UUID uuid = readUuid(entry);
                if (identity.isBlank() || data.identities.containsKey(identity)) {
                    throw new IllegalArgumentException("Missing or duplicate research identity: " + identity);
                }
                data.identities.put(identity, uuid);
            });
        }
        for (Tag value : data.entries(tag, "unlocks")) {
            data.readEntry("unlocks", value, entry -> {
                UUID uuid = readUuid(entry);
                if (data.unlocks.containsKey(uuid)) {
                    throw new IllegalArgumentException("Duplicate research unlock subject: " + uuid);
                }
                Tag nodes = entry.get("nodes");
                if (!(nodes instanceof ListTag nodeList) || (!nodeList.isEmpty() && nodeList.getElementType() != Tag.TAG_STRING)) {
                    throw new IllegalArgumentException("Research unlock nodes must be a string list");
                }
                data.unlocks.put(uuid, ResearchNbt.readIds(entry, "nodes"));
            });
        }
        for (Tag value : data.entries(tag, "stations")) {
            @Nullable
            UUID uuid = null;
            try {
                CompoundTag entry = compound(value);
                uuid = readUuid(entry);
                if (data.stations.containsKey(uuid) || data.quarantined.containsKey(uuid)) {
                    throw new IllegalArgumentException("Duplicate research station UUID: " + uuid);
                }
                data.stations.put(uuid, ResearchStationState.load(entry, registries));
            } catch (RuntimeException error) {
                if (uuid != null && !data.stations.containsKey(uuid) && !data.quarantined.containsKey(uuid)) {
                    data.quarantined.put(uuid, (CompoundTag) value.copy());
                    data.setDirty();
                    Data_Energistics.LOGGER.error("Quarantined research station {} without discarding its journal", uuid, error);
                } else {
                    data.quarantine("stations", value, error);
                }
            }
        }
        return data;
    }

    private ListTag entries(CompoundTag tag, String section) {
        Tag value = tag.get(section);
        if (value == null) return new ListTag();
        if (value instanceof ListTag entries) return entries;
        quarantine(section, value, new IllegalArgumentException("Research section must be a list"));
        return new ListTag();
    }

    private void readEntry(String section, Tag value, Consumer<CompoundTag> reader) {
        try {
            reader.accept(compound(value));
        } catch (RuntimeException error) {
            quarantine(section, value, error);
        }
    }

    private void quarantine(String section, Tag value, RuntimeException error) {
        CompoundTag rejected = new CompoundTag();
        rejected.putString("section", section);
        rejected.put("value", value.copy());
        quarantinedEntries.add(rejected);
        setDirty();
        Data_Energistics.LOGGER.error("Quarantined malformed research {} entry without discarding its original NBT", section, error);
    }

    private static CompoundTag compound(Tag value) {
        if (value instanceof CompoundTag entry) return entry;
        throw new IllegalArgumentException("Research entry must be a compound tag");
    }

    private static UUID readUuid(CompoundTag entry) {
        if (!entry.hasUUID("uuid")) throw new IllegalArgumentException("Missing or malformed research UUID");
        return entry.getUUID("uuid");
    }
}
