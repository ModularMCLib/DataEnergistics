package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.proof;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.deterministic.TrinityDeterministicCycleSequence;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.seed.TrinityCycleSeedRequirement;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.schedule.TrinityVariantFiring;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.topology.TrinityStronglyConnectedComponent;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;
import com.fish_dan_.data_energistics.util.FastUtilCollections;
import com.fish_dan_.data_energistics.util.TrinityDeterministicFiringMath;

import appeng.api.stacks.AEKey;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;

import java.math.BigInteger;
import java.util.Optional;

/**
 * Exact quantity-independent unit route and restart seed for one deterministic productive cycle axis.
 *
 * @param reservoir     productive internal output
 * @param order         stable unit firing order
 * @param firings       unit aggregate firing vector
 * @param netChange     unit exact net change
 * @param internalSeed  SCC-internal restart reserve
 * @param externalInput per-unit external prefix input
 */
public record TrinityCycleUnitProof(
                                    AEKey reservoir,
                                    ObjectList<TrinityVariantFiring> order,
                                    Object2ObjectMap<TrinityPatternVariant, BigInteger> firings,
                                    Object2ObjectMap<AEKey, BigInteger> netChange,
                                    Object2ObjectMap<AEKey, BigInteger> internalSeed,
                                    Object2ObjectMap<AEKey, BigInteger> externalInput) {

    /** Derives a proof only for a complete unique-producer component route. */
    public static Optional<TrinityCycleUnitProof> derive(
                                                         TrinityStronglyConnectedComponent component,
                                                         AEKey reservoir) {
        Optional<ObjectList<TrinityVariantFiring>> resolved = TrinityDeterministicCycleSequence.create()
                .resolve(component, reservoir, FastUtilCollections.mapOf(), ObjectSet.of());
        if (resolved.isEmpty() || !completeUniqueRoute(component, resolved.orElseThrow())) {
            return Optional.empty();
        }
        ObjectList<TrinityVariantFiring> order = resolved.orElseThrow();
        Object2ObjectMap<TrinityPatternVariant, BigInteger> firings = FastUtilCollections.immutableMap(
                new Object2ObjectLinkedOpenHashMap<>(TrinityDeterministicFiringMath.aggregate(order)));
        Object2ObjectMap<AEKey, BigInteger> net = TrinityDeterministicFiringMath.netChange(firings);
        ObjectSet<AEKey> internalKeys = new ObjectOpenHashSet<>(component.keys());
        Object2ObjectMap<AEKey, BigInteger> minimumInputs = TrinityCycleSeedRequirement.minimumInputs(order);
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> internalSeed = new Object2ObjectLinkedOpenHashMap<>();
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> externalInput = new Object2ObjectLinkedOpenHashMap<>();
        minimumInputs.forEach((key, amount) -> (internalKeys.contains(key) ? internalSeed : externalInput)
                .put(key, amount));
        return Optional.of(new TrinityCycleUnitProof(
                reservoir,
                order,
                firings,
                net,
                FastUtilCollections.immutableMap(internalSeed),
                FastUtilCollections.immutableMap(externalInput)));
    }

    /**
     * Reorders the cached unit firing ratio against current inventory and recomputes its exact prefix reserves.
     */
    public TrinityCycleUnitProof instantiate(
                                             Object2ObjectMap<AEKey, BigInteger> available,
                                             ObjectList<AEKey> internalKeys,
                                             ObjectSet<AEKey> producibleInputs) {
        ObjectArrayList<TrinityVariantFiring> remaining = new ObjectArrayList<>(order);
        ObjectArrayList<TrinityVariantFiring> ordered = new ObjectArrayList<>(order.size());
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> balances = new Object2ObjectLinkedOpenHashMap<>(available);
        ObjectSet<AEKey> internal = new ObjectOpenHashSet<>(internalKeys);
        ObjectSet<AEKey> startupKeys = new ObjectOpenHashSet<>(internal);
        startupKeys.removeAll(producibleInputs);
        while (!remaining.isEmpty()) {
            TrinityVariantFiring selected = remaining.stream()
                    .filter(firing -> hasInputs(balances, requiredAtStart(firing), startupKeys))
                    .findFirst()
                    .orElse(remaining.getFirst());
            remaining.remove(selected);
            ordered.add(selected);
            selected.variant().netChange().forEach((key, amount) -> balances.merge(
                    key,
                    amount.multiply(selected.count()),
                    BigInteger::add));
        }
        Object2ObjectMap<AEKey, BigInteger> minimumInputs = TrinityCycleSeedRequirement.minimumInputs(ordered);
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> newInternalSeed = new Object2ObjectLinkedOpenHashMap<>();
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> newExternalInput = new Object2ObjectLinkedOpenHashMap<>();
        minimumInputs.forEach((key, amount) -> (internal.contains(key) ? newInternalSeed : newExternalInput)
                .put(key, amount));
        return new TrinityCycleUnitProof(
                reservoir,
                FastUtilCollections.immutableList(ordered),
                firings,
                netChange,
                FastUtilCollections.immutableMap(newInternalSeed),
                FastUtilCollections.immutableMap(newExternalInput));
    }

    private static Object2ObjectMap<AEKey, BigInteger> requiredAtStart(TrinityVariantFiring firing) {
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> required = new Object2ObjectLinkedOpenHashMap<>();
        firing.variant().inputs().forEach((key, input) -> {
            BigInteger net = firing.variant().netChange().getOrDefault(key, BigInteger.ZERO);
            required.put(key, net.signum() < 0 ?
                    input.add(net.negate().multiply(firing.count().subtract(BigInteger.ONE))) : input);
        });
        return required;
    }

    private static boolean hasInputs(
                                     Object2ObjectMap<AEKey, BigInteger> balances,
                                     Object2ObjectMap<AEKey, BigInteger> required,
                                     ObjectSet<AEKey> internalKeys) {
        return required.entrySet().stream().allMatch(entry -> !internalKeys.contains(entry.getKey()) || balances
                .getOrDefault(entry.getKey(), BigInteger.ZERO)
                .compareTo(entry.getValue()) >= 0);
    }

    private static boolean completeUniqueRoute(
                                               TrinityStronglyConnectedComponent component,
                                               ObjectList<TrinityVariantFiring> order) {
        ObjectSet<TrinityPatternVariant> selected = new ObjectOpenHashSet<>();
        order.forEach(firing -> selected.add(firing.variant()));
        if (selected.size() != order.size() ||
                !selected.equals(new ObjectOpenHashSet<>(component.cycleVariants()))) {
            return false;
        }
        return component.keys().stream().allMatch(key -> component.cycleVariants().stream().noneMatch(variant -> variant.netChange().containsKey(key)) ||
                component.cycleVariants().stream()
                        .filter(variant -> variant.netChange().getOrDefault(key, BigInteger.ZERO).signum() > 0)
                        .limit(2L)
                        .count() == 1L);
    }
}
