package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.deterministic.proof;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnosticCode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningControl;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.TrinityCycleDemand;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.deterministic.TrinityDeterministicComponentPlan;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.deterministic.applicability.TrinityDeterministicBasis;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.deterministic.firing.TrinityDeterministicFiringSolution;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.deterministic.support.TrinityDeterministicDiagnostics;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.seed.TrinityCycleSeedRequirement;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.schedule.TrinityCompressedSchedule;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.schedule.TrinityDeterministicRepeatScheduler;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.schedule.TrinityVariantFiring;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.topology.TrinityStronglyConnectedComponent;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;
import com.fish_dan_.data_energistics.util.FastUtilCollections;
import com.fish_dan_.data_energistics.util.TrinityDeterministicFiringMath;

import appeng.api.stacks.AEKey;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectSet;

import java.math.BigInteger;

/**
 * Converts an exact deterministic firing vector into an executable seed, prefix, repeat, and suffix proof.
 * <p>
 * Reconstructs an executable compressed schedule and verifies its exact balances without objective refinement.
 */
public final class TrinityDeterministicProofAssembler {

    /**
     * Creates the proof assembler from the exact repeated-cycle scheduler.
     */
    public static TrinityDeterministicProofAssembler create(TrinityDeterministicRepeatScheduler repeatScheduler) {
        return new TrinityDeterministicProofAssembler(repeatScheduler);
    }

    private final TrinityDeterministicRepeatScheduler repeatScheduler;

    TrinityDeterministicProofAssembler(TrinityDeterministicRepeatScheduler repeatScheduler) {
        this.repeatScheduler = repeatScheduler;
    }

    /**
     * Proves exact conservation and compressed executability for the first stable constructive firing vector.
     */
    public TrinityAlgorithmResult<TrinityDeterministicComponentPlan> assemble(
                                                                              TrinityStronglyConnectedComponent component,
                                                                              TrinityCycleDemand demand,
                                                                              Object2ObjectMap<AEKey, BigInteger> available,
                                                                              ObjectSet<AEKey> producibleInputs,
                                                                              TrinityDeterministicFiringSolution firingSolution,
                                                                              int maxStates,
                                                                              TrinityPlanningControl control) {
        if (maxStates <= 0) {
            throw new IllegalArgumentException("A deterministic proof assembly request is incomplete");
        }
        TrinityDeterministicBasis basis = firingSolution.basis();
        Object2ObjectMap<TrinityPatternVariant, BigInteger> firings = firingSolution.firings();
        Object2ObjectMap<AEKey, BigInteger> totalNet = firingSolution.totalNet();
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> conservationInputs = conservationInputs(
                component,
                demand,
                totalNet);
        CycleDecomposition decomposition = decompose(
                basis.primitiveFirings(),
                firings,
                basis.reservoir(),
                basis.residualTopology().executionOrder());
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> initialInputs = new Object2ObjectLinkedOpenHashMap<>(conservationInputs);
        applyRequiredPrefix(decomposition.prefixOrder(), initialInputs);
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> cycleStart = simulate(
                initialInputs,
                decomposition.prefixOrder());
        Object2ObjectMap<AEKey, BigInteger> cycleMaximum = cycleStartMaximum(
                component,
                basis.primitiveFirings(),
                available,
                producibleInputs,
                cycleStart,
                decomposition.prefixOrder());
        TrinityAlgorithmResult<NormalizedCycle> normalized = normalizePrimitiveCycle(
                component,
                basis.primitiveOrder(),
                cycleStart,
                cycleMaximum,
                maxStates,
                control);
        if (!normalized.successful()) {
            return TrinityAlgorithmResult.failure(normalized.diagnostic());
        }
        Object2ObjectMap<AEKey, BigInteger> requiredCycleStart = normalized.value().initialBalances();
        if (decomposition.repetitions().signum() > 0) {
            Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> repeatedStart = new Object2ObjectLinkedOpenHashMap<>(requiredCycleStart);
            TrinityCycleSeedRequirement.repeatedMinimumInputs(
                    normalized.value().order(),
                    decomposition.repetitions()).forEach(
                            (key, amount) -> repeatedStart.merge(key, amount, BigInteger::max));
            requiredCycleStart = FastUtilCollections.immutableMap(repeatedStart);
        }
        mergeRequiredCycleStart(initialInputs, cycleStart, requiredCycleStart);
        applyRequiredSuffix(
                decomposition.suffixOrder(),
                decomposition.prefixOrder(),
                basis.primitiveNet(),
                decomposition.repetitions(),
                initialInputs);

        int totalStates = Math.addExact(
                normalized.value().statesVisited(),
                firingSolution.balancePasses());
        if (totalStates > maxStates) {
            return TrinityDeterministicDiagnostics.searchLimit(maxStates, totalStates);
        }
        Object2ObjectMap<AEKey, BigInteger> minimumSeed = internalAmounts(initialInputs, component.keys());
        int remainingStates = Math.subtractExact(maxStates, totalStates);
        if (remainingStates <= 0) {
            return TrinityDeterministicDiagnostics.searchLimit(maxStates, totalStates);
        }
        TrinityAlgorithmResult<TrinityCompressedSchedule> scheduled = schedule(
                decomposition.prefixOrder(),
                normalized.value().order(),
                decomposition.repetitions(),
                decomposition.suffixOrder(),
                initialInputs,
                remainingStates,
                control);
        if (!scheduled.successful()) {
            return TrinityAlgorithmResult.failure(scheduled.diagnostic());
        }
        totalStates = Math.addExact(totalStates, scheduled.value().statesVisited());
        if (totalStates > maxStates) {
            return TrinityDeterministicDiagnostics.searchLimit(maxStates, totalStates);
        }
        TrinityCompressedSchedule completeSchedule = scheduled.value().withStatesVisited(totalStates);
        TrinityDeterministicComponentPlan plan = new TrinityDeterministicComponentPlan(
                firings,
                minimumSeed,
                initialInputs,
                totalNet,
                completeSchedule);
        return TrinityAlgorithmResult.success(plan);
    }

    private static TrinityAlgorithmResult<NormalizedCycle> normalizePrimitiveCycle(
                                                                                   TrinityStronglyConnectedComponent component,
                                                                                   ObjectList<TrinityVariantFiring> primitiveOrder,
                                                                                   Object2ObjectMap<AEKey, BigInteger> minimumBalances,
                                                                                   Object2ObjectMap<AEKey, BigInteger> maximumBalances,
                                                                                   int maxStates,
                                                                                   TrinityPlanningControl control) {
        TrinityDeterministicDiagnostics.StopState stop = TrinityDeterministicDiagnostics.stopState(control);
        if (stop != TrinityDeterministicDiagnostics.StopState.RUNNING) {
            return TrinityDeterministicDiagnostics.stopped(stop);
        }
        int states = Math.addExact(primitiveOrder.size(), 1);
        if (states > maxStates) {
            return TrinityDeterministicDiagnostics.searchLimit(maxStates, states);
        }
        ObjectSet<AEKey> internalKeys = FastUtilCollections.immutableSet(component.keys());
        ObjectLinkedOpenHashSet<AEKey> externalKeys = new ObjectLinkedOpenHashSet<>();
        primitiveOrder.forEach(firing -> firing.variant().inputs().keySet().forEach(key -> {
            if (!internalKeys.contains(key)) {
                externalKeys.add(key);
            }
        }));
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> requiredBalances = new Object2ObjectLinkedOpenHashMap<>(
                schedulableInputBalances(minimumBalances, externalKeys, internalKeys));
        TrinityCycleSeedRequirement.minimumInputs(primitiveOrder).forEach(
                (key, amount) -> requiredBalances.merge(key, amount, BigInteger::max));
        if (requiredBalances.entrySet().stream().anyMatch(entry -> entry.getValue().compareTo(
                maximumBalances.getOrDefault(entry.getKey(), TrinityDeterministicFiringMath.ZERO)) > 0)) {
            return TrinityDeterministicDiagnostics.failure(
                    TrinityPlanningDiagnosticCode.NO_EXECUTABLE_ORDER,
                    TrinityDeterministicDiagnostics.NO_EXECUTABLE_ORDER_KEY,
                    FastUtilCollections.mapOf("states", Integer.toString(states)));
        }
        return TrinityAlgorithmResult.success(new NormalizedCycle(
                FastUtilCollections.immutableList(primitiveOrder),
                FastUtilCollections.immutableMap(requiredBalances),
                states));
    }

    private static Object2ObjectMap<AEKey, BigInteger> schedulableInputBalances(
                                                                                Object2ObjectMap<AEKey, BigInteger> balances,
                                                                                ObjectSet<AEKey> externalKeys,
                                                                                ObjectSet<AEKey> internalKeys) {
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> inputs = new Object2ObjectLinkedOpenHashMap<>();
        balances.forEach((key, amount) -> {
            if (externalKeys.contains(key) || internalKeys.contains(key)) {
                inputs.put(key, amount);
            }
        });
        return FastUtilCollections.immutableMap(inputs);
    }

    private TrinityAlgorithmResult<TrinityCompressedSchedule> schedule(
                                                                       ObjectList<TrinityVariantFiring> prefixOrder,
                                                                       ObjectList<TrinityVariantFiring> baseOrder,
                                                                       BigInteger repetitions,
                                                                       ObjectList<TrinityVariantFiring> suffixOrder,
                                                                       Object2ObjectMap<AEKey, BigInteger> initialInputs,
                                                                       int maxStates,
                                                                       TrinityPlanningControl control) {
        if (maxStates <= 0) {
            return TrinityDeterministicDiagnostics.searchLimit(0, 0);
        }
        ObjectArrayList<TrinityVariantFiring> prefixBatches = new ObjectArrayList<>();
        ObjectArrayList<TrinityVariantFiring> suffixBatches = new ObjectArrayList<>();
        ObjectList<TrinityVariantFiring> repeatUnit = ObjectList.of();
        BigInteger repeatCount = BigInteger.ZERO;
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> balances = new Object2ObjectLinkedOpenHashMap<>(initialInputs);
        int states = 1;
        for (TrinityVariantFiring firing : prefixOrder) {
            TrinityAlgorithmResult<Integer> executed = executeBatch(
                    firing,
                    balances,
                    prefixBatches,
                    states,
                    maxStates,
                    control);
            if (!executed.successful()) {
                return TrinityAlgorithmResult.failure(executed.diagnostic());
            }
            states = executed.value();
        }
        if (repetitions.signum() > 0) {
            int remainingStates = Math.subtractExact(maxStates, states);
            if (remainingStates <= 0) {
                return TrinityDeterministicDiagnostics.searchLimit(maxStates, states);
            }
            TrinityAlgorithmResult<TrinityCompressedSchedule> repeated = this.repeatScheduler.schedule(
                    baseOrder,
                    repetitions,
                    positiveBalances(balances),
                    remainingStates,
                    control);
            if (!repeated.successful()) {
                return repeated;
            }
            if (!repeated.value().hasRepeatBlock()) {
                throw new IllegalStateException("A deterministic repeat scheduler returned a flat proof");
            }
            repeatUnit = repeated.value().repeatUnit();
            repeatCount = repeated.value().repeatCount();
            balances.clear();
            balances.putAll(repeated.value().finalBalances());
            states = Math.addExact(states, repeated.value().statesVisited());
        }
        for (TrinityVariantFiring firing : suffixOrder) {
            TrinityAlgorithmResult<Integer> executed = executeBatch(
                    firing,
                    balances,
                    suffixBatches,
                    states,
                    maxStates,
                    control);
            if (!executed.successful()) {
                return TrinityAlgorithmResult.failure(executed.diagnostic());
            }
            states = executed.value();
        }
        Object2ObjectMap<AEKey, BigInteger> finalBalances = positiveBalances(balances);
        if (repeatCount.signum() > 0) {
            return TrinityAlgorithmResult.success(TrinityCompressedSchedule.repeated(
                    prefixBatches,
                    repeatUnit,
                    repeatCount,
                    suffixBatches,
                    finalBalances,
                    states));
        }
        prefixBatches.addAll(suffixBatches);
        return TrinityAlgorithmResult.success(new TrinityCompressedSchedule(prefixBatches, finalBalances, states));
    }

    private static TrinityAlgorithmResult<Integer> executeBatch(
                                                                TrinityVariantFiring firing,
                                                                Object2ObjectMap<AEKey, BigInteger> balances,
                                                                ObjectList<TrinityVariantFiring> batches,
                                                                int states,
                                                                int maxStates,
                                                                TrinityPlanningControl control) {
        TrinityDeterministicDiagnostics.StopState state = TrinityDeterministicDiagnostics.stopState(control);
        if (state != TrinityDeterministicDiagnostics.StopState.RUNNING) {
            return TrinityDeterministicDiagnostics.stopped(state);
        }
        if (states >= maxStates) {
            return TrinityDeterministicDiagnostics.searchLimit(maxStates, states);
        }
        if (lacksInputs(balances, requiredAtStart(firing))) {
            return TrinityDeterministicDiagnostics.failure(
                    TrinityPlanningDiagnosticCode.NO_EXECUTABLE_ORDER,
                    TrinityDeterministicDiagnostics.NO_EXECUTABLE_ORDER_KEY,
                    FastUtilCollections.mapOf("variant", firing.variant().patternIdentity().publicationEncoding()));
        }
        apply(firing, balances);
        appendBatch(batches, firing);
        return TrinityAlgorithmResult.success(Math.incrementExact(states));
    }

    private static CycleDecomposition decompose(
                                                Object2ObjectMap<TrinityPatternVariant, BigInteger> primitiveFirings,
                                                Object2ObjectMap<TrinityPatternVariant, BigInteger> firings,
                                                AEKey reservoir,
                                                ObjectList<TrinityPatternVariant> topologicalOrder) {
        BigInteger repetitions = null;
        for (Object2ObjectMap.Entry<TrinityPatternVariant, BigInteger> primitive : primitiveFirings.object2ObjectEntrySet()) {
            BigInteger available = firings.getOrDefault(
                    primitive.getKey(),
                    TrinityDeterministicFiringMath.ZERO);
            BigInteger supported = available.divide(primitive.getValue());
            repetitions = repetitions == null ? supported : repetitions.min(supported);
        }
        if (repetitions == null) {
            throw new IllegalStateException("A deterministic Trinity basis cannot be empty");
        }
        Object2ObjectLinkedOpenHashMap<TrinityPatternVariant, BigInteger> residual = new Object2ObjectLinkedOpenHashMap<>();
        for (TrinityPatternVariant variant : topologicalOrder) {
            BigInteger count = firings.getOrDefault(variant, TrinityDeterministicFiringMath.ZERO)
                    .subtract(primitiveFirings
                            .getOrDefault(variant, TrinityDeterministicFiringMath.ZERO)
                            .multiply(repetitions));
            if (count.signum() < 0) {
                throw new IllegalStateException("A shifted Trinity vector cannot underflow its primitive decomposition");
            }
            if (count.signum() > 0) {
                residual.put(variant, count);
            }
        }
        ObjectArrayList<TrinityVariantFiring> prefix = new ObjectArrayList<>();
        ObjectArrayList<TrinityVariantFiring> suffix = new ObjectArrayList<>();
        residual.forEach((variant, count) -> {
            TrinityVariantFiring firing = new TrinityVariantFiring(variant, count);
            if (variant.netChange().getOrDefault(reservoir, TrinityDeterministicFiringMath.ZERO).signum() > 0) {
                prefix.add(firing);
            } else {
                suffix.add(firing);
            }
        });
        return new CycleDecomposition(
                repetitions,
                FastUtilCollections.immutableList(prefix),
                FastUtilCollections.immutableList(suffix));
    }

    private static Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> conservationInputs(
                                                                                        TrinityStronglyConnectedComponent component,
                                                                                        TrinityCycleDemand demand,
                                                                                        Object2ObjectMap<AEKey, BigInteger> netChange) {
        ObjectLinkedOpenHashSet<AEKey> keys = new ObjectLinkedOpenHashSet<>();
        component.cycleVariants().forEach(variant -> keys.addAll(variant.netChange().keySet()));
        keys.addAll(demand.finalBalanceLowerBounds().keySet());
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> inputs = new Object2ObjectLinkedOpenHashMap<>();
        for (AEKey key : keys) {
            BigInteger required = demand.finalBalanceLowerBounds()
                    .getOrDefault(key, TrinityDeterministicFiringMath.ZERO)
                    .subtract(netChange.getOrDefault(key, TrinityDeterministicFiringMath.ZERO))
                    .max(TrinityDeterministicFiringMath.ZERO);
            if (required.signum() > 0) {
                inputs.put(key, required);
            }
        }
        return inputs;
    }

    private static void applyRequiredPrefix(
                                            ObjectList<TrinityVariantFiring> prefix,
                                            Object2ObjectMap<AEKey, BigInteger> initialInputs) {
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> balances = new Object2ObjectLinkedOpenHashMap<>(initialInputs);
        for (TrinityVariantFiring firing : prefix) {
            requiredAtStart(firing).forEach((key, required) -> {
                BigInteger deficit = required.subtract(
                        balances.getOrDefault(key, TrinityDeterministicFiringMath.ZERO));
                if (deficit.signum() > 0) {
                    initialInputs.merge(key, deficit, BigInteger::add);
                    balances.merge(key, deficit, BigInteger::add);
                }
            });
            apply(firing, balances);
        }
    }

    private static Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> simulate(
                                                                              Object2ObjectMap<AEKey, BigInteger> initial,
                                                                              ObjectList<TrinityVariantFiring> order) {
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> balances = new Object2ObjectLinkedOpenHashMap<>(initial);
        for (TrinityVariantFiring firing : order) {
            if (lacksInputs(balances, requiredAtStart(firing))) {
                throw new IllegalStateException("A derived Trinity prefix is not executable from its reserved inputs");
            }
            apply(firing, balances);
        }
        return balances;
    }

    private static Object2ObjectMap<AEKey, BigInteger> cycleStartMaximum(
                                                                         TrinityStronglyConnectedComponent component,
                                                                         Object2ObjectMap<TrinityPatternVariant, BigInteger> primitiveFirings,
                                                                         Object2ObjectMap<AEKey, BigInteger> available,
                                                                         ObjectSet<AEKey> producibleInputs,
                                                                         Object2ObjectMap<AEKey, BigInteger> cycleStart,
                                                                         ObjectList<TrinityVariantFiring> prefix) {
        Object2ObjectMap<AEKey, BigInteger> prefixNet = TrinityDeterministicFiringMath.netChange(
                TrinityDeterministicFiringMath.aggregate(prefix));
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> primitiveConsumption = new Object2ObjectLinkedOpenHashMap<>();
        primitiveFirings.forEach((variant, count) -> variant.inputs().forEach(
                (key, amount) -> primitiveConsumption.merge(key, amount.multiply(count), BigInteger::add)));
        ObjectLinkedOpenHashSet<AEKey> keys = new ObjectLinkedOpenHashSet<>(component.keys());
        primitiveFirings.keySet().forEach(variant -> keys.addAll(variant.inputs().keySet()));
        keys.addAll(cycleStart.keySet());
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> maximum = new Object2ObjectLinkedOpenHashMap<>();
        for (AEKey key : keys) {
            BigInteger value;
            if (producibleInputs.contains(key)) {
                value = cycleStart.getOrDefault(key, TrinityDeterministicFiringMath.ZERO)
                        .add(primitiveConsumption.getOrDefault(key, TrinityDeterministicFiringMath.ZERO));
            } else {
                value = available.getOrDefault(key, TrinityDeterministicFiringMath.ZERO)
                        .add(prefixNet.getOrDefault(key, TrinityDeterministicFiringMath.ZERO));
            }
            value = value.max(cycleStart.getOrDefault(key, TrinityDeterministicFiringMath.ZERO));
            if (value.signum() < 0) {
                throw new IllegalStateException("A Trinity prefix exceeds a finite cycle-start balance");
            }
            maximum.put(key, value);
        }
        return FastUtilCollections.immutableMap(maximum);
    }

    private static void mergeRequiredCycleStart(
                                                Object2ObjectMap<AEKey, BigInteger> initialInputs,
                                                Object2ObjectMap<AEKey, BigInteger> currentCycleStart,
                                                Object2ObjectMap<AEKey, BigInteger> requiredCycleStart) {
        requiredCycleStart.forEach((key, required) -> {
            BigInteger deficit = required.subtract(
                    currentCycleStart.getOrDefault(key, TrinityDeterministicFiringMath.ZERO));
            if (deficit.signum() > 0) {
                initialInputs.merge(key, deficit, BigInteger::add);
            }
        });
    }

    private static void applyRequiredSuffix(
                                            ObjectList<TrinityVariantFiring> suffixOrder,
                                            ObjectList<TrinityVariantFiring> prefixOrder,
                                            Object2ObjectMap<AEKey, BigInteger> primitiveNet,
                                            BigInteger repetitions,
                                            Object2ObjectMap<AEKey, BigInteger> initialInputs) {
        Object2ObjectMap<AEKey, BigInteger> suffixMinimum = TrinityCycleSeedRequirement.minimumInputs(suffixOrder);
        Object2ObjectMap<AEKey, BigInteger> beforeSuffixNet = TrinityDeterministicFiringMath.addSigned(
                TrinityDeterministicFiringMath.netChange(TrinityDeterministicFiringMath.aggregate(prefixOrder)),
                TrinityDeterministicFiringMath.multiplySigned(primitiveNet, repetitions));
        suffixMinimum.forEach((key, required) -> {
            BigInteger initialRequired = required.subtract(
                    beforeSuffixNet.getOrDefault(key, TrinityDeterministicFiringMath.ZERO));
            if (initialRequired.signum() > 0) {
                initialInputs.merge(key, initialRequired, BigInteger::max);
            }
        });
    }

    private static Object2ObjectMap<AEKey, BigInteger> internalAmounts(
                                                                       Object2ObjectMap<AEKey, BigInteger> amounts,
                                                                       ObjectList<AEKey> internalKeys) {
        ObjectSet<AEKey> internal = FastUtilCollections.immutableSet(internalKeys);
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> selected = new Object2ObjectLinkedOpenHashMap<>();
        amounts.forEach((key, amount) -> {
            if (internal.contains(key) && amount.signum() > 0) {
                selected.put(key, amount);
            }
        });
        return FastUtilCollections.immutableMap(selected);
    }

    private static Object2ObjectMap<AEKey, BigInteger> requiredAtStart(TrinityVariantFiring firing) {
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> required = new Object2ObjectLinkedOpenHashMap<>();
        firing.variant().inputs().forEach((key, input) -> {
            BigInteger net = firing.variant().netChange()
                    .getOrDefault(key, TrinityDeterministicFiringMath.ZERO);
            BigInteger amount = net.signum() < 0 ?
                    input.add(net.negate().multiply(firing.count().subtract(BigInteger.ONE))) :
                    input;
            required.put(key, amount);
        });
        return FastUtilCollections.immutableMap(required);
    }

    private static void apply(TrinityVariantFiring firing, Object2ObjectMap<AEKey, BigInteger> balances) {
        firing.variant().netChange().forEach((key, amount) -> {
            BigInteger updated = balances.getOrDefault(key, TrinityDeterministicFiringMath.ZERO)
                    .add(amount.multiply(firing.count()));
            if (updated.signum() < 0) {
                throw new IllegalStateException("A deterministic Trinity batch produced a negative balance");
            }
            if (updated.signum() == 0) {
                balances.remove(key);
            } else {
                balances.put(key, updated);
            }
        });
    }

    private static boolean lacksInputs(
                                       Object2ObjectMap<AEKey, BigInteger> balances,
                                       Object2ObjectMap<AEKey, BigInteger> required) {
        return required.entrySet().stream().anyMatch(entry -> balances
                .getOrDefault(entry.getKey(), TrinityDeterministicFiringMath.ZERO)
                .compareTo(entry.getValue()) < 0);
    }

    private static void appendBatch(
                                    ObjectList<TrinityVariantFiring> batches,
                                    TrinityVariantFiring added) {
        if (!batches.isEmpty() && batches.getLast().variant().equals(added.variant())) {
            TrinityVariantFiring previous = batches.getLast();
            batches.set(
                    batches.size() - 1,
                    new TrinityVariantFiring(previous.variant(), previous.count().add(added.count())));
            return;
        }
        batches.add(added);
    }

    private static Object2ObjectMap<AEKey, BigInteger> positiveBalances(Object2ObjectMap<AEKey, BigInteger> balances) {
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> positive = new Object2ObjectLinkedOpenHashMap<>();
        balances.forEach((key, amount) -> {
            if (amount.signum() > 0) {
                positive.put(key, amount);
            }
        });
        return FastUtilCollections.immutableMap(positive);
    }

    private record NormalizedCycle(
                                   ObjectList<TrinityVariantFiring> order,
                                   Object2ObjectMap<AEKey, BigInteger> initialBalances,
                                   int statesVisited) {}

    private record CycleDecomposition(
                                      BigInteger repetitions,
                                      ObjectList<TrinityVariantFiring> prefixOrder,
                                      ObjectList<TrinityVariantFiring> suffixOrder) {}
}
