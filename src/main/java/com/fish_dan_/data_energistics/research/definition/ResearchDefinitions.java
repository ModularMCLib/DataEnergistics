package com.fish_dan_.data_energistics.research.definition;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.research.ResearchNode;

import net.minecraft.resources.ResourceLocation;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMaps;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.jspecify.annotations.Nullable;

/** Java registrations plus an atomically published, server-owned datapack snapshot. */
public final class ResearchDefinitions {

    private static final Object2ObjectMap<ResourceLocation, ResearchNode> DEFAULTS = new Object2ObjectLinkedOpenHashMap<>();
    private static volatile Object2ObjectMap<ResourceLocation, ResearchNode> current = Object2ObjectMaps.emptyMap();
    private static boolean frozen;
    private static long revision;

    private ResearchDefinitions() {}

    public static void register(ResearchNode node) {
        if (frozen || DEFAULTS.putIfAbsent(node.id(), node) != null) {
            throw new IllegalStateException("Research registration closed or duplicate ID: " + node.id());
        }
    }

    public static Object2ObjectMap<ResourceLocation, ResearchNode> defaults() {
        frozen = true;
        return new Object2ObjectLinkedOpenHashMap<>(DEFAULTS);
    }

    public static void publish(Object2ObjectMap<ResourceLocation, ResearchNode> definitions) {
        ObjectSet<ResourceLocation> rejected = new ObjectOpenHashSet<>();
        boolean changed;
        do {
            int rejectedCount = rejected.size();
            for (ResourceLocation id : definitions.keySet()) {
                if (rejected.contains(id)) continue;
                ResearchNode node = definitions.get(id);
                for (ResourceLocation exclusiveId : node.mutuallyExclusive()) {
                    if (!definitions.containsKey(exclusiveId)) {
                        rejected.add(id);
                        Data_Energistics.LOGGER.error("Rejected research file {} field mutually_exclusive: missing node {}",
                                definitionFile(id), exclusiveId);
                        continue;
                    }
                    ResearchNode exclusive = definitions.get(exclusiveId);
                    if (rejected.contains(exclusiveId)) {
                        rejected.add(id);
                        Data_Energistics.LOGGER.error("Rejected research file {} field mutually_exclusive: referenced node {} is unavailable",
                                definitionFile(id), exclusiveId);
                        continue;
                    }
                    if (exclusive.scope() != node.scope()) {
                        rejected.add(id);
                        rejected.add(exclusiveId);
                        Data_Energistics.LOGGER.error("Rejected research files {} and {} field mutually_exclusive: nodes must share a scope",
                                definitionFile(id), definitionFile(exclusiveId));
                    } else if (!exclusive.mutuallyExclusive().contains(id)) {
                        rejected.add(id);
                        rejected.add(exclusiveId);
                        Data_Energistics.LOGGER.error("Rejected research files {} and {} field mutually_exclusive: references must be reciprocal",
                                definitionFile(id), definitionFile(exclusiveId));
                    }
                }
                if (!valid(id, definitions, rejected, new ObjectOpenHashSet<>(), new ObjectOpenHashSet<>())) {
                    rejected.add(id);
                    Data_Energistics.LOGGER.error("Rejected research file {} field prerequisites: missing or cyclic prerequisite",
                            definitionFile(id));
                }
            }
            changed = rejected.size() != rejectedCount;
        } while (changed);
        rejected.forEach(definitions::remove);
        current = Object2ObjectMaps.unmodifiable(new Object2ObjectLinkedOpenHashMap<>(definitions));
        revision++;
    }

    private static boolean valid(ResourceLocation id, Object2ObjectMap<ResourceLocation, ResearchNode> definitions,
                                 ObjectSet<ResourceLocation> rejected,
                                 ObjectSet<ResourceLocation> visiting, ObjectSet<ResourceLocation> visited) {
        if (visited.contains(id)) return true;
        if (rejected.contains(id) || !definitions.containsKey(id)) return false;
        ResearchNode node = definitions.get(id);
        if (!visiting.add(id)) return false;
        for (ResourceLocation prerequisite : node.prerequisiteIds()) {
            if (!valid(prerequisite, definitions, rejected, visiting, visited)) return false;
        }
        visiting.remove(id);
        visited.add(id);
        return true;
    }

    public static Object2ObjectMap<ResourceLocation, ResearchNode> nodes() {
        return current;
    }

    public static @Nullable ResearchNode find(ResourceLocation id) {
        return current.get(id);
    }

    public static long revision() {
        return revision;
    }

    private static String definitionFile(ResourceLocation id) {
        return "data/" + id.getNamespace() + "/researchtechnology/" + id.getPath() + ".json";
    }
}
