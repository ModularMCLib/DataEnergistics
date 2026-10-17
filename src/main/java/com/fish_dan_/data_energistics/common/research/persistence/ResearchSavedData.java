package com.fish_dan_.data_energistics.common.research.persistence;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.research.ResearchNode;
import com.fish_dan_.data_energistics.api.research.ResearchScope;
import com.fish_dan_.data_energistics.common.research.crafting.ResearchMaterialRequests;
import com.fish_dan_.data_energistics.common.research.progression.ResearchProject;
import com.fish_dan_.data_energistics.common.research.progression.ResearchStationState;

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
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import org.jspecify.annotations.Nullable;

import java.util.UUID;
import java.util.function.Consumer;

/** Overworld-owned identity bridge, unlock ledger and station journals. Access on the server thread only. */
public final class ResearchSavedData extends SavedData {

    private static final Factory<ResearchSavedData> FACTORY = new Factory<>(ResearchSavedData::new, ResearchSavedData::load);
    private final Object2ObjectMap<String, UUID> identities = new Object2ObjectLinkedOpenHashMap<>();
    private final Object2ObjectMap<UUID, ObjectSet<ResourceLocation>> unlocks = new Object2ObjectLinkedOpenHashMap<>();
    private final Object2ObjectMap<UUID, ObjectSet<ResourceLocation>> playerUpgrades = new Object2ObjectLinkedOpenHashMap<>();
    private final ObjectSet<UUID> instantTechnologySubjects = new ObjectOpenHashSet<>();
    private final ObjectSet<UUID> instantPlayerSubjects = new ObjectOpenHashSet<>();
    private final Object2ObjectMap<UUID, Object2ObjectMap<ResourceLocation, ResourceLocation>> technologyExclusions = new Object2ObjectLinkedOpenHashMap<>();
    private final Object2ObjectMap<UUID, Object2ObjectMap<ResourceLocation, ResourceLocation>> playerExclusions = new Object2ObjectLinkedOpenHashMap<>();
    private final Object2ObjectMap<UUID, ResearchStationState> stations = new Object2ObjectLinkedOpenHashMap<>();
    private final Object2ObjectMap<UUID, CompoundTag> quarantined = new Object2ObjectLinkedOpenHashMap<>();
    private final ObjectList<Tag> quarantinedEntries = new ObjectArrayList<>();
    private final ReferenceOpenHashSet<ResearchMaterialRequests> materialCalculations = new ReferenceOpenHashSet<>();

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

    public ObjectSet<ResourceLocation> playerUpgrades(UUID player) {
        return ObjectSets.unmodifiable(playerUpgrades.getOrDefault(player, ObjectSets.emptySet()));
    }

    public boolean hasInstantTechnology(Iterable<UUID> subjects) {
        for (UUID subject : subjects) if (instantTechnologySubjects.contains(subject)) return true;
        return false;
    }

    public boolean hasInstantPlayerResearch(UUID player) {
        return instantPlayerSubjects.contains(player);
    }

    public void enableInstantTechnology(Iterable<UUID> subjects) {
        boolean changed = false;
        for (UUID subject : subjects) changed |= instantTechnologySubjects.add(subject);
        if (changed) setDirty();
    }

    public void enableInstantPlayerResearch(UUID player) {
        if (instantPlayerSubjects.add(player)) setDirty();
    }

    public @Nullable ResourceLocation technologyExclusionWinner(Iterable<UUID> subjects, ResourceLocation blocked) {
        for (UUID subject : subjects) {
            Object2ObjectMap<ResourceLocation, ResourceLocation> locks = technologyExclusions.get(subject);
            if (locks != null && locks.containsKey(blocked)) return locks.get(blocked);
        }
        return null;
    }

    public @Nullable ResourceLocation playerExclusionWinner(UUID player, ResourceLocation blocked) {
        Object2ObjectMap<ResourceLocation, ResourceLocation> locks = playerExclusions.get(player);
        return locks == null ? null : locks.get(blocked);
    }

    /** Registers transient planning work for server-thread polling independently of loaded block entities. */
    public void trackMaterialCalculations(ResearchMaterialRequests requests) {
        materialCalculations.add(requests);
    }

    /** Polls only active queues once in the server's post tick; no station journals are scanned. */
    public void tickMaterialCalculations(MinecraftServer server) {
        var iterator = materialCalculations.iterator();
        while (iterator.hasNext()) {
            ResearchMaterialRequests requests = iterator.next();
            requests.tick(server);
            if (!requests.hasPendingCalculations()) iterator.remove();
        }
    }

    /** Stops transient calculations before the server releases its worlds; paid projects remain saved. */
    public void cancelMaterialCalculations() {
        materialCalculations.forEach(ResearchMaterialRequests::cancelCalculations);
        materialCalculations.clear();
    }

    public void unlockTechnology(Iterable<UUID> subjects, ResearchNode node) {
        boolean changed = false;
        for (UUID subject : subjects) {
            changed |= unlocks.computeIfAbsent(subject, unused -> new ObjectOpenHashSet<>()).add(node.id());
            if (!node.mutuallyExclusive().isEmpty()) {
                Object2ObjectMap<ResourceLocation, ResourceLocation> locks = technologyExclusions.computeIfAbsent(
                        subject, unused -> new Object2ObjectLinkedOpenHashMap<>());
                for (ResourceLocation blocked : node.mutuallyExclusive()) {
                    changed |= locks.putIfAbsent(blocked, node.id()) == null;
                }
            }
        }
        if (changed) setDirty();
    }

    public void unlockPlayerUpgrade(UUID player, ResearchNode node) {
        boolean changed = playerUpgrades.computeIfAbsent(player, unused -> new ObjectOpenHashSet<>()).add(node.id());
        if (!node.mutuallyExclusive().isEmpty()) {
            Object2ObjectMap<ResourceLocation, ResourceLocation> locks = playerExclusions.computeIfAbsent(
                    player, unused -> new Object2ObjectLinkedOpenHashMap<>());
            for (ResourceLocation blocked : node.mutuallyExclusive()) changed |= locks.putIfAbsent(blocked, node.id()) == null;
        }
        if (changed) setDirty();
    }

    /** Finds a running project on any station that conflicts for the selected scope owner. */
    public @Nullable ResourceLocation runningMutualProject(ResearchNode node, ObjectSet<UUID> subjects,
                                                           UUID initiator, UUID station) {
        for (var stationEntry : stations.object2ObjectEntrySet()) {
            if (node.scope() == ResearchScope.ITEM_UPGRADE && !station.equals(stationEntry.getKey())) continue;
            for (ResearchProject project : stationEntry.getValue().projects.values()) {
                if (project.running && mutuallyExclusive(node, project.node) &&
                        sameOwner(node.scope(), subjects, initiator, project)) {
                    return project.node.id();
                }
            }
        }
        return null;
    }

    /** Removes conflicting projects after a node wins; paid materials are intentionally not refunded. */
    public void cancelMutuallyExclusiveProjects(ResearchNode completedNode, ObjectSet<UUID> completedSubjects,
                                                UUID initiator, UUID sourceStation) {
        boolean changed = false;
        for (var stationEntry : stations.object2ObjectEntrySet()) {
            ResearchStationState state = stationEntry.getValue();
            ObjectList<ResourceLocation> cancelled = new ObjectArrayList<>();
            for (ResearchProject project : state.projects.values()) {
                if (project.node.id().equals(completedNode.id()) || !mutuallyExclusive(completedNode, project.node)) continue;
                if (completedNode.scope() == ResearchScope.ITEM_UPGRADE && !sourceStation.equals(stationEntry.getKey())) continue;
                if (!sameOwner(completedNode.scope(), completedSubjects, initiator, project)) continue;
                cancelled.add(project.node.id());
            }
            if (cancelled.isEmpty()) continue;
            for (ResourceLocation id : cancelled) state.projects.remove(id);
            state.revision++;
            changed = true;
        }
        if (changed) setDirty();
    }

    private static boolean sameOwner(ResearchScope scope, ObjectSet<UUID> subjects, UUID initiator, ResearchProject project) {
        return switch (scope) {
            case TECHNOLOGY -> {
                boolean shared = false;
                for (UUID subject : subjects) {
                    if (project.subjects.contains(subject)) {
                        shared = true;
                        break;
                    }
                }
                yield shared;
            }
            case ITEM_UPGRADE -> true;
            case PLAYER_UPGRADE -> initiator.equals(project.initiator);
        };
    }

    private static boolean mutuallyExclusive(ResearchNode first, ResearchNode second) {
        return first.mutuallyExclusive().contains(second.id()) || second.mutuallyExclusive().contains(first.id());
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
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
        tag.put("player_upgrades", ResearchNbt.uuidIds(playerUpgrades));
        tag.put("instant_technology_subjects", ResearchNbt.uuids(instantTechnologySubjects));
        tag.put("instant_player_subjects", ResearchNbt.uuids(instantPlayerSubjects));
        tag.put("technology_exclusions", ResearchNbt.uuidExclusions(technologyExclusions));
        tag.put("player_exclusions", ResearchNbt.uuidExclusions(playerExclusions));
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
        for (Tag value : data.entries(tag, "player_upgrades")) {
            data.readEntry("player_upgrades", value, entry -> {
                UUID uuid = readUuid(entry);
                if (data.playerUpgrades.putIfAbsent(uuid, ResearchNbt.readIds(entry, "nodes")) != null) {
                    throw new IllegalArgumentException("Duplicate player upgrade UUID: " + uuid);
                }
            });
        }
        data.readUuidFlags(tag, "instant_technology_subjects", data.instantTechnologySubjects);
        data.readUuidFlags(tag, "instant_player_subjects", data.instantPlayerSubjects);
        data.readExclusionLedger(tag, "technology_exclusions", data.technologyExclusions);
        data.readExclusionLedger(tag, "player_exclusions", data.playerExclusions);
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

    private void readExclusionLedger(CompoundTag tag, String field,
                                     Object2ObjectMap<UUID, Object2ObjectMap<ResourceLocation, ResourceLocation>> target) {
        for (Tag value : entries(tag, field)) {
            readEntry(field, value, entry -> {
                UUID uuid = readUuid(entry);
                if (target.putIfAbsent(uuid, ResearchNbt.readExclusions(entry, "locks")) != null) {
                    throw new IllegalArgumentException("Duplicate exclusion owner UUID: " + uuid);
                }
            });
        }
    }

    private void readUuidFlags(CompoundTag tag, String field, ObjectSet<UUID> target) {
        for (Tag value : entries(tag, field)) {
            readEntry(field, value, entry -> {
                UUID uuid = readUuid(entry);
                if (!target.add(uuid)) throw new IllegalArgumentException("Duplicate research UUID flag: " + uuid);
            });
        }
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
