package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.selection;

import com.fish_dan_.data_energistics.util.FastUtilCollections;

import it.unimi.dsi.fastutil.objects.Object2ObjectMap;

/**
 * Immutable request facts used to select a CPU without reading live grid objects.
 *
 * @param requiredBytes    job storage requirement
 * @param playerRequest    whether the action source contains a player
 * @param prioritizePower  whether AE2 should prefer more co-processors
 * @param roundRobinStarts next stable identity for each hardware-equivalent group
 */
public record CraftingCpuSelectionRequest(long requiredBytes,
                                          boolean playerRequest,
                                          boolean prioritizePower,
                                          Object2ObjectMap<CraftingCpuSelectionGroup, String> roundRobinStarts) {

    public CraftingCpuSelectionRequest {
        if (requiredBytes < 0L) {
            throw new IllegalArgumentException("Crafting job storage requirement must not be negative");
        }
        if (roundRobinStarts == null) {
            throw new IllegalArgumentException("Crafting CPU round-robin starts must not be null");
        }
        for (Object2ObjectMap.Entry<CraftingCpuSelectionGroup, String> entry : roundRobinStarts.object2ObjectEntrySet()) {
            if (entry.getKey() == null || entry.getValue() == null || entry.getValue().isBlank()) {
                throw new IllegalArgumentException("Crafting CPU round-robin starts must contain valid groups and identities");
            }
        }
        roundRobinStarts = FastUtilCollections.immutableMap(roundRobinStarts);
    }
}
