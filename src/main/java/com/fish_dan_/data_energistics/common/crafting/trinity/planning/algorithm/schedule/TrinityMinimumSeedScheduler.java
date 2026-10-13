package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.schedule;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnostic;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnosticCode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningControl;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;
import com.fish_dan_.data_energistics.util.FastUtilCollections;

import appeng.api.stacks.AEKey;

import net.minecraft.network.chat.Component;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;

import java.math.BigInteger;
import java.util.Collections;
import java.util.Comparator;
import java.util.Optional;
import java.util.PriorityQueue;

/**
 * Finds the minimum exact internal seed for a fixed MIP firing vector using bounded compressed scheduling.
 * <p>
 * Dijkstra-style seed search: ordinary batches cost zero and exact seed injections cost their added item units.
 */
public final class TrinityMinimumSeedScheduler {

    /**
     * @return stateless bounded scheduler
     */
    public static TrinityMinimumSeedScheduler create() {
        return new TrinityMinimumSeedScheduler();
    }

    private static final String CANCELLED_KEY = "gui.data_energistics.trinity_planning.diagnostic.cancelled";
    private static final String SEARCH_LIMIT_KEY = "gui.data_energistics.trinity_planning.diagnostic.search_limit";
    private static final String NO_EXECUTABLE_ORDER_KEY = "gui.data_energistics.trinity_planning.diagnostic.no_executable_order";

    /**
     * @param firings       positive MIP firing vector
     * @param externalKeys  boundary keys charged by the first lexicographic objective
     * @param seedableKeys  internal SCC keys charged by the second lexicographic objective
     * @param minimumInputs conservation-required balances injected before schedule search
     * @param maximumInputs stock or upstream-production upper bound for each injected key
     * @param maxStates     compressed state limit
     * @param control       cancellation and deadline boundary
     * @return minimum seed and executable schedule, or stable rejection
     */
    public TrinityAlgorithmResult<TrinityMinimumSeedSchedule> find(
                                                                   Object2ObjectMap<TrinityPatternVariant, BigInteger> firings,
                                                                   ObjectSet<AEKey> externalKeys,
                                                                   ObjectSet<AEKey> seedableKeys,
                                                                   Object2ObjectMap<AEKey, BigInteger> minimumInputs,
                                                                   Object2ObjectMap<AEKey, BigInteger> maximumInputs,
                                                                   int maxStates,
                                                                   TrinityPlanningControl control) {
        return search(
                firings,
                externalKeys,
                seedableKeys,
                minimumInputs,
                maximumInputs,
                null,
                false,
                maxStates,
                control);
    }

    /**
     * Finds the minimum true prefix seed for a fixed firing vector after the global external objective has been fixed.
     * External injections may use any exact distribution whose total does not exceed {@code externalTotal}; unused
     * units can then be added without affecting executability.
     *
     * @param externalTotal exact global external-input budget
     */
    public TrinityAlgorithmResult<TrinityMinimumSeedSchedule> findWithinExternalTotal(
                                                                                      Object2ObjectMap<TrinityPatternVariant, BigInteger> firings,
                                                                                      ObjectSet<AEKey> externalKeys,
                                                                                      ObjectSet<AEKey> seedableKeys,
                                                                                      Object2ObjectMap<AEKey, BigInteger> minimumInputs,
                                                                                      Object2ObjectMap<AEKey, BigInteger> maximumInputs,
                                                                                      BigInteger externalTotal,
                                                                                      int maxStates,
                                                                                      TrinityPlanningControl control) {
        if (externalTotal == null || externalTotal.signum() < 0) {
            throw new IllegalArgumentException("A Trinity fixed external total cannot be negative or null");
        }
        return search(
                firings,
                externalKeys,
                seedableKeys,
                minimumInputs,
                maximumInputs,
                externalTotal,
                true,
                maxStates,
                control);
    }

    private TrinityAlgorithmResult<TrinityMinimumSeedSchedule> search(
                                                                      Object2ObjectMap<TrinityPatternVariant, BigInteger> firings,
                                                                      ObjectSet<AEKey> externalKeys,
                                                                      ObjectSet<AEKey> seedableKeys,
                                                                      Object2ObjectMap<AEKey, BigInteger> minimumInputs,
                                                                      Object2ObjectMap<AEKey, BigInteger> maximumInputs,
                                                                      BigInteger externalLimit,
                                                                      boolean seedFirst,
                                                                      int maxStates,
                                                                      TrinityPlanningControl control) {
        if (firings == null || firings.isEmpty() || externalKeys == null || seedableKeys == null ||
                minimumInputs == null || maximumInputs == null || maxStates <= 0 || control == null) {
            throw new IllegalArgumentException(
                    "A Trinity seed search requires complete inputs and a positive state limit");
        }

        ObjectList<TrinityPatternVariant> variants = firings.keySet().stream().sorted().collect(ObjectArrayList.toList());
        ObjectList<BigInteger> remaining = variants.stream().map(variant -> requirePositive(firings.get(variant))).collect(ObjectArrayList.toList());
        if (!Collections.disjoint(externalKeys, seedableKeys)) {
            throw new IllegalArgumentException("A Trinity input key cannot be both external and internal seed");
        }
        ObjectLinkedOpenHashSet<AEKey> allBalanceKeys = new ObjectLinkedOpenHashSet<>(externalKeys);
        allBalanceKeys.addAll(seedableKeys);
        allBalanceKeys.addAll(minimumInputs.keySet());
        allBalanceKeys.addAll(maximumInputs.keySet());
        ObjectList<AEKey> keys = TrinityCompressedScheduler.relevantKeys(variants, toZeroMap(allBalanceKeys));
        validateInputBounds(externalKeys, seedableKeys, minimumInputs, maximumInputs);
        ObjectList<BigInteger> balances = keys.stream()
                .map(key -> minimumInputs.getOrDefault(key, BigInteger.ZERO))
                .collect(ObjectArrayList.toList());
        ObjectList<BigInteger> external = categoryVector(keys, minimumInputs, externalKeys);
        ObjectList<BigInteger> seed = categoryVector(keys, minimumInputs, seedableKeys);
        BigInteger externalUnits = external.stream().reduce(BigInteger.ZERO, BigInteger::add);
        BigInteger seedUnits = seed.stream().reduce(BigInteger.ZERO, BigInteger::add);
        if (externalLimit != null && externalUnits.compareTo(externalLimit) > 0) {
            return failure(
                    TrinityPlanningDiagnosticCode.NO_EXECUTABLE_ORDER,
                    NO_EXECUTABLE_ORDER_KEY,
                    FastUtilCollections.mapOf("states", "0"));
        }

        Comparator<SearchNode> ordering = seedFirst ? Comparator
                .comparing(SearchNode::seedUnits)
                .thenComparing(SearchNode::externalUnits) :
                Comparator
                        .comparing(SearchNode::externalUnits)
                        .thenComparing(SearchNode::seedUnits);
        // Costs stay authoritative; within one cost layer, depth-first expansion follows compressed maximum batches.
        ordering = ordering
                .thenComparing(Comparator.comparingInt(
                        (SearchNode node) -> node.batches().size()).reversed())
                .thenComparingLong(SearchNode::sequence);
        PriorityQueue<SearchNode> pending = new PriorityQueue<>(ordering);
        long sequence = 0L;
        pending.add(new SearchNode(
                remaining,
                balances,
                external,
                seed,
                ObjectList.of(),
                externalUnits,
                seedUnits,
                sequence++));
        ObjectOpenHashSet<StateKey> visited = new ObjectOpenHashSet<>();
        int statesVisited = 0;
        while (!pending.isEmpty()) {
            if (control.cancellationRequested()) {
                return failure(
                        TrinityPlanningDiagnosticCode.CALCULATION_CANCELLED,
                        CANCELLED_KEY,
                        FastUtilCollections.mapOf("states", Integer.toString(statesVisited)));
            }
            if (control.deadlineExceeded()) {
                return failure(
                        TrinityPlanningDiagnosticCode.ORDER_SEARCH_LIMIT,
                        SEARCH_LIMIT_KEY,
                        FastUtilCollections.mapOf("reason", "timeout", "states", Integer.toString(statesVisited)));
            }

            SearchNode node = pending.remove();
            if (!visited.add(new StateKey(node.remaining(), node.balances()))) {
                continue;
            }
            statesVisited = Math.addExact(statesVisited, 1);
            if (TrinityCompressedScheduler.allComplete(node.remaining())) {
                return TrinityAlgorithmResult.success(new TrinityMinimumSeedSchedule(
                        positiveVector(keys, node.external()),
                        positiveVector(keys, node.seed()),
                        new TrinityCompressedSchedule(
                                node.batches(),
                                TrinityCompressedScheduler.positiveBalances(keys, node.balances()),
                                statesVisited)));
            }
            if (statesVisited >= maxStates) {
                return failure(
                        TrinityPlanningDiagnosticCode.ORDER_SEARCH_LIMIT,
                        SEARCH_LIMIT_KEY,
                        FastUtilCollections.mapOf(
                                "limit", Integer.toString(maxStates),
                                "states", Integer.toString(statesVisited)));
            }

            for (int variantIndex = 0; variantIndex < variants.size(); variantIndex++) {
                BigInteger count = node.remaining().get(variantIndex);
                if (count.signum() == 0) {
                    continue;
                }
                TrinityPatternVariant variant = variants.get(variantIndex);
                BigInteger safe = TrinityCompressedScheduler.maximumSafeBatch(
                        variant,
                        count,
                        keys,
                        node.balances());
                if (safe.signum() > 0) {
                    sequence = enqueueBatches(
                            pending,
                            sequence,
                            variants,
                            keys,
                            node,
                            variantIndex,
                            safe,
                            node.balances(),
                            node.external(),
                            node.seed(),
                            node.externalUnits(),
                            node.seedUnits());
                    continue;
                }

                Optional<SeedInjection> injection = requiredInjection(
                        variant,
                        keys,
                        node.balances(),
                        node.external(),
                        node.seed(),
                        externalKeys,
                        seedableKeys,
                        maximumInputs);
                if (injection.isEmpty()) {
                    continue;
                }
                SeedInjection required = injection.orElseThrow();
                if (externalLimit != null && required.externalUnits().compareTo(externalLimit) > 0) {
                    continue;
                }
                BigInteger injectedSafe = TrinityCompressedScheduler.maximumSafeBatch(
                        variant,
                        count,
                        keys,
                        required.balances());
                if (injectedSafe.signum() <= 0) {
                    throw new IllegalStateException("An exact Trinity seed injection did not enable its transition");
                }
                sequence = enqueueBatches(
                        pending,
                        sequence,
                        variants,
                        keys,
                        node,
                        variantIndex,
                        injectedSafe,
                        required.balances(),
                        required.external(),
                        required.seed(),
                        required.externalUnits(),
                        required.seedUnits());
            }
        }
        return failure(
                TrinityPlanningDiagnosticCode.NO_EXECUTABLE_ORDER,
                NO_EXECUTABLE_ORDER_KEY,
                FastUtilCollections.mapOf("states", Integer.toString(statesVisited)));
    }

    private static long enqueueBatches(
                                       PriorityQueue<SearchNode> pending,
                                       long nextSequence,
                                       ObjectList<TrinityPatternVariant> variants,
                                       ObjectList<AEKey> keys,
                                       SearchNode node,
                                       int variantIndex,
                                       BigInteger maximum,
                                       ObjectList<BigInteger> startingBalances,
                                       ObjectList<BigInteger> external,
                                       ObjectList<BigInteger> seed,
                                       BigInteger externalUnits,
                                       BigInteger seedUnits) {
        TrinityPatternVariant variant = variants.get(variantIndex);
        for (BigInteger batch : TrinityCompressedScheduler.batchCandidates(
                variants,
                variant,
                maximum,
                keys,
                startingBalances)) {
            ObjectArrayList<BigInteger> remaining = new ObjectArrayList<>(node.remaining());
            remaining.set(variantIndex, remaining.get(variantIndex).subtract(batch));
            ObjectArrayList<BigInteger> balances = new ObjectArrayList<>(startingBalances);
            for (int keyIndex = 0; keyIndex < keys.size(); keyIndex++) {
                BigInteger delta = variant.netChange().getOrDefault(keys.get(keyIndex), BigInteger.ZERO);
                BigInteger updated = balances.get(keyIndex).add(delta.multiply(batch));
                if (updated.signum() < 0) {
                    throw new IllegalStateException("A seeded Trinity batch produced a negative balance");
                }
                balances.set(keyIndex, updated);
            }
            ObjectArrayList<TrinityVariantFiring> batches = new ObjectArrayList<>(node.batches());
            batches.add(new TrinityVariantFiring(variant, batch));
            pending.add(new SearchNode(
                    FastUtilCollections.immutableList(remaining),
                    FastUtilCollections.immutableList(balances),
                    external,
                    seed,
                    FastUtilCollections.immutableList(batches),
                    externalUnits,
                    seedUnits,
                    nextSequence++));
        }
        return nextSequence;
    }

    private static Optional<SeedInjection> requiredInjection(
                                                             TrinityPatternVariant variant,
                                                             ObjectList<AEKey> keys,
                                                             ObjectList<BigInteger> balances,
                                                             ObjectList<BigInteger> external,
                                                             ObjectList<BigInteger> seed,
                                                             ObjectSet<AEKey> externalKeys,
                                                             ObjectSet<AEKey> seedableKeys,
                                                             Object2ObjectMap<AEKey, BigInteger> maximumInputs) {
        ObjectArrayList<BigInteger> injectedBalances = new ObjectArrayList<>(balances);
        ObjectArrayList<BigInteger> injectedExternal = new ObjectArrayList<>(external);
        ObjectArrayList<BigInteger> injectedSeed = new ObjectArrayList<>(seed);
        BigInteger addedExternal = BigInteger.ZERO;
        BigInteger addedSeed = BigInteger.ZERO;
        for (Object2ObjectMap.Entry<AEKey, BigInteger> input : variant.inputs().object2ObjectEntrySet()) {
            int keyIndex = keys.indexOf(input.getKey());
            BigInteger deficit = input.getValue().subtract(injectedBalances.get(keyIndex));
            if (deficit.signum() <= 0) {
                continue;
            }
            boolean externalInput = externalKeys.contains(input.getKey());
            if (!externalInput && !seedableKeys.contains(input.getKey())) {
                return Optional.empty();
            }
            ObjectList<BigInteger> category = externalInput ? injectedExternal : injectedSeed;
            BigInteger newAmount = category.get(keyIndex).add(deficit);
            if (newAmount.compareTo(maximumInputs.getOrDefault(input.getKey(), BigInteger.ZERO)) > 0) {
                return Optional.empty();
            }
            category.set(keyIndex, newAmount);
            injectedBalances.set(keyIndex, injectedBalances.get(keyIndex).add(deficit));
            if (externalInput) {
                addedExternal = addedExternal.add(deficit);
            } else {
                addedSeed = addedSeed.add(deficit);
            }
        }
        if (addedExternal.signum() == 0 && addedSeed.signum() == 0) {
            return Optional.empty();
        }
        BigInteger previousExternal = external.stream().reduce(BigInteger.ZERO, BigInteger::add);
        BigInteger previousUnits = seed.stream().reduce(BigInteger.ZERO, BigInteger::add);
        return Optional.of(new SeedInjection(
                FastUtilCollections.immutableList(injectedBalances),
                FastUtilCollections.immutableList(injectedExternal),
                FastUtilCollections.immutableList(injectedSeed),
                previousExternal.add(addedExternal),
                previousUnits.add(addedSeed)));
    }

    private static void validateInputBounds(ObjectSet<AEKey> externalKeys,
                                            ObjectSet<AEKey> seedableKeys,
                                            Object2ObjectMap<AEKey, BigInteger> minimumInputs,
                                            Object2ObjectMap<AEKey, BigInteger> maximumInputs) {
        for (AEKey key : externalKeys) {
            if (key == null) {
                throw new IllegalArgumentException("A Trinity external key cannot be null");
            }
        }
        for (AEKey key : seedableKeys) {
            if (key == null) {
                throw new IllegalArgumentException("A Trinity seedable key cannot be null");
            }
        }
        maximumInputs.forEach((key, amount) -> {
            if (key == null || amount == null || amount.signum() < 0) {
                throw new IllegalArgumentException("A Trinity seed bound cannot be negative or null");
            }
        });
        minimumInputs.forEach((key, amount) -> {
            if (key == null || amount == null || amount.signum() < 0 ||
                    (!externalKeys.contains(key) && !seedableKeys.contains(key))) {
                throw new IllegalArgumentException("A Trinity input lower bound is invalid");
            }
            if (amount.compareTo(maximumInputs.getOrDefault(key, BigInteger.ZERO)) > 0) {
                throw new IllegalArgumentException("A Trinity input lower bound exceeds its upper bound");
            }
        });
    }

    private static Object2ObjectMap<AEKey, BigInteger> toZeroMap(ObjectSet<AEKey> keys) {
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> zero = new Object2ObjectLinkedOpenHashMap<>();
        keys.forEach(key -> zero.put(key, BigInteger.ZERO));
        return zero;
    }

    private static ObjectList<BigInteger> categoryVector(
                                                         ObjectList<AEKey> keys,
                                                         Object2ObjectMap<AEKey, BigInteger> minimumInputs,
                                                         ObjectSet<AEKey> categoryKeys) {
        return keys.stream()
                .map(key -> categoryKeys.contains(key) ?
                        minimumInputs.getOrDefault(key, BigInteger.ZERO) :
                        BigInteger.ZERO)
                .collect(ObjectArrayList.toList());
    }

    private static Object2ObjectMap<AEKey, BigInteger> positiveVector(ObjectList<AEKey> keys, ObjectList<BigInteger> values) {
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> positive = new Object2ObjectLinkedOpenHashMap<>();
        for (int index = 0; index < keys.size(); index++) {
            if (values.get(index).signum() > 0) {
                positive.put(keys.get(index), values.get(index));
            }
        }
        return positive;
    }

    private static BigInteger requirePositive(BigInteger value) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException("A Trinity seed-search firing count must be positive");
        }
        return value;
    }

    private static <T> TrinityAlgorithmResult<T> failure(
                                                         TrinityPlanningDiagnosticCode code,
                                                         String translationKey,
                                                         Object2ObjectMap<String, String> metadata) {
        return TrinityAlgorithmResult.failure(new TrinityPlanningDiagnostic(
                code,
                Component.translatable(translationKey),
                metadata));
    }

    private record SearchNode(
                              ObjectList<BigInteger> remaining,
                              ObjectList<BigInteger> balances,
                              ObjectList<BigInteger> external,
                              ObjectList<BigInteger> seed,
                              ObjectList<TrinityVariantFiring> batches,
                              BigInteger externalUnits,
                              BigInteger seedUnits,
                              long sequence) {}

    private record StateKey(ObjectList<BigInteger> remaining, ObjectList<BigInteger> balances) {}

    private record SeedInjection(
                                 ObjectList<BigInteger> balances,
                                 ObjectList<BigInteger> external,
                                 ObjectList<BigInteger> seed,
                                 BigInteger externalUnits,
                                 BigInteger seedUnits) {}
}
