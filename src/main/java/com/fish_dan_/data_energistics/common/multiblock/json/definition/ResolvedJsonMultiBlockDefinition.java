package com.fish_dan_.data_energistics.common.multiblock.json.definition;

import com.fish_dan_.data_energistics.common.compartment.CompartmentType;
import com.fish_dan_.data_energistics.common.multiblock.json.autobuild.JsonMultiBlockAutoBuildStaging;

import com.modularmc.mdl.api.multiblock.BlockPattern;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMaps;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import it.unimi.dsi.fastutil.objects.ObjectSets;

import java.util.Optional;

/**
 * Eager immutable definition already resolved from JSON or built-in factories.
 */
public record ResolvedJsonMultiBlockDefinition(JsonMultiBlockStructureKey key,
                                               BlockPattern pattern,
                                               Optional<String> displayNameTranslationKey,
                                               Object2ObjectMap<String, CompartmentType> compartmentTypes,
                                               Object2ObjectMap<String, ObjectSet<CompartmentType>> replaceableCompartmentTypes,
                                               JsonMultiBlockAutoBuildStaging autoBuildStaging)
        implements JsonMultiBlockDefinition {

    public ResolvedJsonMultiBlockDefinition {
        compartmentTypes = Object2ObjectMaps.unmodifiable(new Object2ObjectLinkedOpenHashMap<>(compartmentTypes));
        replaceableCompartmentTypes = copyReplaceableCompartmentTypes(replaceableCompartmentTypes);
    }

    public ResolvedJsonMultiBlockDefinition(JsonMultiBlockStructureKey key,
                                            BlockPattern pattern,
                                            Optional<String> displayNameTranslationKey,
                                            Object2ObjectMap<String, CompartmentType> compartmentTypes,
                                            Object2ObjectMap<String, ObjectSet<CompartmentType>> replaceableCompartmentTypes) {
        this(
                key,
                pattern,
                displayNameTranslationKey,
                compartmentTypes,
                replaceableCompartmentTypes,
                JsonMultiBlockAutoBuildStaging.none());
    }

    public ResolvedJsonMultiBlockDefinition(JsonMultiBlockStructureKey key,
                                            BlockPattern pattern,
                                            Optional<String> displayNameTranslationKey) {
        this(key, pattern, displayNameTranslationKey, Object2ObjectMaps.emptyMap(), Object2ObjectMaps.emptyMap(), JsonMultiBlockAutoBuildStaging.none());
    }

    public ResolvedJsonMultiBlockDefinition(JsonMultiBlockStructureKey key, BlockPattern pattern) {
        this(key, pattern, Optional.empty(), Object2ObjectMaps.emptyMap(), Object2ObjectMaps.emptyMap(), JsonMultiBlockAutoBuildStaging.none());
    }

    private static Object2ObjectMap<String, ObjectSet<CompartmentType>> copyReplaceableCompartmentTypes(
                                                                                                        Object2ObjectMap<String, ObjectSet<CompartmentType>> source) {
        Object2ObjectLinkedOpenHashMap<String, ObjectSet<CompartmentType>> copy = new Object2ObjectLinkedOpenHashMap<>();
        for (Object2ObjectMap.Entry<String, ObjectSet<CompartmentType>> entry : source.object2ObjectEntrySet()) {
            copy.put(entry.getKey(), ObjectSets.unmodifiable(new ObjectOpenHashSet<>(entry.getValue())));
        }
        return Object2ObjectMaps.unmodifiable(copy);
    }
}
