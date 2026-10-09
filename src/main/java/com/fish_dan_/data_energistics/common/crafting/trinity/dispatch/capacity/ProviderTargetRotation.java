package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.capacity;

import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.async.model.CraftingDispatchCursor;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.CraftingProviderId;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.ProviderCapacitySnapshot;
import com.fish_dan_.data_energistics.util.FastUtilCollections;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;

/**
 * Pure hierarchical rotation that interleaves one target from each provider before visiting later targets.
 */
final class ProviderTargetRotation {

    private final ObjectList<Target> targets;

    private ProviderTargetRotation(ObjectList<Target> targets) {
        this.targets = FastUtilCollections.immutableList(targets);
    }

    /**
     * Builds a complete provider-first, target-second order without dropping duplicate snapshot values.
     */
    static ProviderTargetRotation create(ObjectList<ProviderCapacitySnapshot> snapshots, CraftingDispatchCursor cursor) {
        ObjectList<ProviderCapacitySnapshot> stableSnapshots = FastUtilCollections.immutableList(snapshots);
        if (cursor == null) {
            throw new IllegalArgumentException("Provider target rotation requires a fairness cursor");
        }
        Object2ObjectLinkedOpenHashMap<CraftingProviderId, ObjectArrayList<ProviderCapacitySnapshot>> grouped = new Object2ObjectLinkedOpenHashMap<>();
        for (ProviderCapacitySnapshot snapshot : stableSnapshots) {
            grouped.computeIfAbsent(snapshot.providerId(), ignored -> new ObjectArrayList<>()).add(snapshot);
        }
        if (grouped.isEmpty()) {
            return new ProviderTargetRotation(ObjectList.of());
        }

        ObjectList<Object2ObjectMap.Entry<CraftingProviderId, ObjectArrayList<ProviderCapacitySnapshot>>> providers = FastUtilCollections.immutableList(grouped.object2ObjectEntrySet());
        int providerCount = providers.size();
        int providerStart = Math.floorMod(cursor.provider(), providerCount);
        int maximumTargets = providers.stream().mapToInt(entry -> entry.getValue().size()).max().orElseThrow();
        ObjectArrayList<ObjectArrayList<Target>> rounds = new ObjectArrayList<>(maximumTargets);
        for (int targetRound = 0; targetRound < maximumTargets; targetRound++) {
            rounds.add(new ObjectArrayList<>());
        }
        ObjectArrayList<Target> rotated = new ObjectArrayList<>(stableSnapshots.size());
        for (int providerOffset = 0; providerOffset < providerCount; providerOffset++) {
            int providerIndex = Math.floorMod(providerStart + providerOffset, providerCount);
            ObjectList<ProviderCapacitySnapshot> providerTargets = providers.get(providerIndex).getValue();
            long firstTargetRound = providerIndex < providerStart ?
                    Math.incrementExact(cursor.target()) :
                    cursor.target();
            for (int targetOffset = 0; targetOffset < providerTargets.size(); targetOffset++) {
                long targetRound = Math.addExact(firstTargetRound, targetOffset);
                int targetIndex = Math.floorMod(targetRound, providerTargets.size());
                int nextProvider = Math.floorMod(providerIndex + 1, providerCount);
                long nextTargetRound = providerIndex == providerCount - 1 ?
                        Math.incrementExact(targetRound) :
                        targetRound;
                CraftingDispatchCursor successor = new CraftingDispatchCursor(
                        nextProvider,
                        nextTargetRound);
                rounds.get(targetOffset).add(new Target(providerTargets.get(targetIndex), successor));
            }
        }
        rounds.forEach(rotated::addAll);
        return new ProviderTargetRotation(rotated);
    }

    /** @return immutable complete rotated targets */
    ObjectList<Target> targets() {
        return this.targets;
    }

    /**
     * @param snapshot  immutable capacity observation retained from the capture
     * @param successor cursor suggested only after this exact target receives a real provider call
     */
    record Target(ProviderCapacitySnapshot snapshot, CraftingDispatchCursor successor) {

        Target {
            if (snapshot == null || successor == null) {
                throw new IllegalArgumentException("Rotated provider target must be complete");
            }
        }
    }
}
