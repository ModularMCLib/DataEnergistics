package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.orchestration.demand.availability;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningControl;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.topology.TrinityCraftingTopology;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.inventory.TrinityPlanningInventory;

import appeng.api.stacks.AEKey;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import it.unimi.dsi.fastutil.objects.ObjectSets;

/** Proves unavailable producers from absent leaf materials, without making quantity or cycle feasibility claims. */
public final class TrinityUnavailableProducers {

    private TrinityUnavailableProducers() {}

    /**
     * Computes exclusions once for a request's original inventory. A material is unavailable only if it has neither
     * stock, an unlimited source, nor a remaining producer. Losing an input excludes its consumers and propagates
     * through outputs whose last producer was excluded. All outputs, including remainders, remain potential sources.
     *
     * <p>
     * Cycles without a proved missing external input remain undecided; their seeds, quantities and execution order
     * still belong to the cycle planner. Speculative inventory consumption must never invalidate this snapshot.
     * An interrupted scan returns only exclusions already proved; the caller must check the shared control before
     * searching. Diagnostic passes must use the original producers to expose the actual missing leaf materials.
     */
    public static ObjectSet<TrinityPatternVariant> find(
                                                        TrinityCraftingTopology topology,
                                                        TrinityPlanningInventory inventory,
                                                        TrinityPlanningControl control) {
        ObjectOpenHashSet<TrinityPatternVariant> variants = new ObjectOpenHashSet<>();
        Object2IntOpenHashMap<AEKey> remainingProducers = new Object2IntOpenHashMap<>();
        for (var entry : topology.variantsByOutputKey().entrySet()) {
            if (control.cancellationRequested() || control.deadlineExceeded()) return ObjectSets.emptySet();
            variants.addAll(entry.getValue());
        }
        Object2ObjectOpenHashMap<AEKey, ObjectList<TrinityPatternVariant>> consumers = new Object2ObjectOpenHashMap<>();
        for (TrinityPatternVariant variant : variants) {
            if (control.cancellationRequested() || control.deadlineExceeded()) return ObjectSets.emptySet();
            for (AEKey input : variant.inputs().keySet()) {
                consumers.computeIfAbsent(input, ignored -> new ObjectArrayList<>()).add(variant);
            }
            for (AEKey output : variant.outputs().keySet()) {
                remainingProducers.addTo(output, 1);
            }
        }

        ObjectArrayList<AEKey> unavailable = new ObjectArrayList<>();
        for (AEKey key : topology.componentByKey().keySet()) {
            if (remainingProducers.getInt(key) == 0 && hasNoInventorySource(inventory, key)) {
                unavailable.add(key);
            }
        }
        ObjectOpenHashSet<TrinityPatternVariant> excluded = new ObjectOpenHashSet<>();
        for (int next = 0; next < unavailable.size(); next++) {
            if (control.cancellationRequested() || control.deadlineExceeded()) break;
            ObjectList<TrinityPatternVariant> dependent = consumers.getOrDefault(unavailable.get(next), ObjectLists.emptyList());
            for (TrinityPatternVariant variant : dependent) {
                if (control.cancellationRequested() || control.deadlineExceeded()) {
                    return ObjectSets.unmodifiable(excluded);
                }
                if (!excluded.add(variant)) continue;
                for (AEKey output : variant.outputs().keySet()) {
                    int previous = remainingProducers.addTo(output, -1);
                    if (previous == 1 && hasNoInventorySource(inventory, output)) {
                        unavailable.add(output);
                    }
                }
            }
        }
        return ObjectSets.unmodifiable(excluded);
    }

    private static boolean hasNoInventorySource(TrinityPlanningInventory inventory, AEKey key) {
        return !inventory.unlimited(key) && inventory.finiteAmount(key).signum() <= 0;
    }
}
