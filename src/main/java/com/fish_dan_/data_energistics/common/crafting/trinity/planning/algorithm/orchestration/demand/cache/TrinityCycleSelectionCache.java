package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.orchestration.demand.cache;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.TrinityCycleDemand;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.proof.TrinityCycleUnitProof;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.selection.TrinityCycleSelection;

import appeng.api.stacks.AEKey;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMaps;
import it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import it.unimi.dsi.fastutil.objects.ObjectSets;
import org.jspecify.annotations.Nullable;

import java.math.BigInteger;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Thread-confined LRU owned by one graph demand search, surviving only its journal rollbacks.
 * Topology, mode and limits are fixed by that owner. Complete demand, inventory, supply and instantiated
 * unit-proof values distinguish subproblems. Only fully verified selections are retained; failures never
 * become negative cache entries. The caller must check cancellation/deadline before every lookup.
 */
public final class TrinityCycleSelectionCache {

    private static final int ENTRY_LIMIT = 128;
    private static final int VALUE_LIMIT = 32768;
    private final Object2ObjectLinkedOpenHashMap<Key, Entry> entries = new Object2ObjectLinkedOpenHashMap<>();
    private int retainedValues;

    /** Reuses a proved selection without charging its historical solver time or search states again. */
    public TrinityAlgorithmResult<TrinityCycleSelection> select(
                                                                int componentIndex, TrinityCycleDemand demand,
                                                                Map<AEKey, BigInteger> inventory, Set<AEKey> producibleInputs,
                                                                @Nullable TrinityCycleUnitProof unitProof,
                                                                Supplier<TrinityAlgorithmResult<TrinityCycleSelection>> calculation) {
        TrinityCycleDemand frozenDemand = new TrinityCycleDemand(
                copy(demand.settledWithdrawals()), copy(demand.terminalBalanceLowerBounds()),
                copy(demand.requiredNetChangeLowerBounds()), ObjectSets.unmodifiable(new ObjectOpenHashSet<>(demand.netNewKeys())),
                copy(demand.finalBalanceLowerBounds()));
        Key key = new Key(componentIndex, frozenDemand, copy(inventory),
                ObjectSets.unmodifiable(new ObjectOpenHashSet<>(producibleInputs)), unitProof);
        if (this.entries.containsKey(key)) {
            return TrinityAlgorithmResult.success(this.entries.getAndMoveToLast(key).selection());
        }
        TrinityAlgorithmResult<TrinityCycleSelection> result = calculation.get();
        if (!result.successful()) return result;
        TrinityCycleSelection selected = result.value();
        long values = (long) inventory.size() + producibleInputs.size() +
                demand.settledWithdrawals().size() + demand.terminalBalanceLowerBounds().size() +
                demand.requiredNetChangeLowerBounds().size() + demand.finalBalanceLowerBounds().size() + demand.netNewKeys().size() +
                selected.prefixOrder().size() + selected.localOrder().size() + selected.suffixOrder().size() +
                selected.minimumSeed().size() + selected.initialInputs().size() + selected.netChange().size() +
                selected.exportableNet().size() + selected.retainedSeed().size();
        if (unitProof != null) values += (long) unitProof.order().size() + unitProof.firings().size() +
                unitProof.netChange().size() + unitProof.internalSeed().size() + unitProof.externalInput().size();
        if (values > VALUE_LIMIT) return result;
        while (this.entries.size() >= ENTRY_LIMIT || this.retainedValues + values > VALUE_LIMIT) {
            this.retainedValues -= this.entries.removeFirst().values();
        }
        TrinityCycleSelection cached = new TrinityCycleSelection(
                selected.componentIndex(), new ObjectImmutableList<>(selected.prefixOrder()),
                new ObjectImmutableList<>(selected.localOrder()), selected.repetitions(), new ObjectImmutableList<>(selected.suffixOrder()),
                copy(selected.minimumSeed()), copy(selected.initialInputs()), copy(selected.netChange()), copy(selected.exportableNet()),
                0, 0L, selected.quality(), copy(selected.retainedSeed()), 0);
        this.entries.putAndMoveToLast(key, new Entry(cached, (int) values));
        this.retainedValues += (int) values;
        return result;
    }

    private static Object2ObjectMap<AEKey, BigInteger> copy(Map<AEKey, BigInteger> values) {
        return Object2ObjectMaps.unmodifiable(new Object2ObjectLinkedOpenHashMap<>(values));
    }

    private record Key(int componentIndex, TrinityCycleDemand demand, Object2ObjectMap<AEKey, BigInteger> inventory,
                       ObjectSet<AEKey> producibleInputs, @Nullable TrinityCycleUnitProof unitProof) {}

    private record Entry(TrinityCycleSelection selection, int values) {}
}
