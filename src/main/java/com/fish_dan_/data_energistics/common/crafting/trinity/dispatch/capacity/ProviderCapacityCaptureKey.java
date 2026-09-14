package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.capacity;

import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongMaps;
import it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenHashMap;

import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.CraftingProviderId;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.provider.CountedCraftingProviderAdapters;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.provider.CraftingProviderPublicationIndex;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.List;
import java.util.Map;

/**
 * Complete immutable identity for one server-tick provider-capacity capture.
 *
 * @param gridScope           owning Grid publication scope
 * @param publicationRevision provider publication generation
 * @param capacityRevision    counted-provider contract generation
 * @param capacityEpoch       server tick defining the capacity observation epoch
 * @param providerFingerprint exact ordered provider publications for the live pattern
 * @param patternIdentity     full semantic pattern identity
 * @param inputPrototype      exact per-slot input counters used for provider capacity
 * @param requestedMaximum    positive maximum logical crafts requested from providers
 */
public record ProviderCapacityCaptureKey(
                                         long gridScope,
                                         long publicationRevision,
                                         long capacityRevision,
                                         long capacityEpoch,
                                         List<CraftingProviderId> providerFingerprint,
                                         String patternIdentity,
                                         List<Object2LongMap<AEKey>> inputPrototype,
                                         long requestedMaximum) {

    public ProviderCapacityCaptureKey {
        if (gridScope <= 0L || publicationRevision < 0L || capacityRevision < 0L || capacityEpoch < 0L) {
            throw new IllegalArgumentException("Provider capacity cache revisions must be valid");
        }
        providerFingerprint = List.copyOf(providerFingerprint);
        if (patternIdentity == null || patternIdentity.isBlank()) {
            throw new IllegalArgumentException("Provider capacity cache pattern identity must not be blank");
        }
        inputPrototype = inputPrototype.stream().map(slot -> Object2LongMaps.unmodifiable(new Object2LongLinkedOpenHashMap<>(slot))).toList();
        if (requestedMaximum <= 0L) {
            throw new IllegalArgumentException("Provider capacity cache maximum must be positive");
        }
    }

    /**
     * Freezes all live lookup inputs before the cache is consulted.
     */
    static ProviderCapacityCaptureKey capture(CraftingProviderPublicationIndex publications,
                                              IPatternDetails pattern,
                                              KeyCounter[] prototype,
                                              long requestedMaximum,
                                              String patternIdentity,
                                              long capacityEpoch) {
        if (publications == null || pattern == null || prototype == null) {
            throw new IllegalArgumentException("Provider capacity cache capture context must not be null");
        }
        ObjectArrayList<Object2LongMap<AEKey>> frozenPrototype = new ObjectArrayList<>(prototype.length);
        for (KeyCounter counter : prototype) {
            if (counter == null) {
                throw new IllegalArgumentException("Provider capacity cache prototype slots must not be null");
            }
            Object2LongLinkedOpenHashMap<AEKey> slot = new Object2LongLinkedOpenHashMap<>();
            for (var entry : counter) {
                if (entry.getLongValue() <= 0L || slot.putIfAbsent(entry.getKey(), entry.getLongValue()) != 0L) {
                    throw new IllegalArgumentException("Provider capacity cache prototype must contain unique positive inputs");
                }
            }
            frozenPrototype.add(Object2LongMaps.unmodifiable(slot));
        }
        return new ProviderCapacityCaptureKey(
                publications.publicationScope(),
                publications.publicationRevision(),
                CountedCraftingProviderAdapters.mutationRevision(),
                capacityEpoch,
                publications.providerIdsFor(pattern),
                patternIdentity,
                frozenPrototype,
                requestedMaximum);
    }
}
