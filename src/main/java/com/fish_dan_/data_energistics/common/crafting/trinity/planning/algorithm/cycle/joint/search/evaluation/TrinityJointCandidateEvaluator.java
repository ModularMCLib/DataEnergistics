package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.joint.search.evaluation;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnostic;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnosticCode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningControl;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.TrinityCycleDemand;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.joint.TrinityJointCyclePlan;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.model.TrinityCycleFeasibilitySolution;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.seed.TrinityCycleSeedRequirement;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.optimization.TrinityExactConservationVerifier;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.optimization.TrinityFiringVector;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.optimization.TrinityLexicographicObjective;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.schedule.TrinityCompressedSchedule;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.schedule.TrinityCompressedScheduler;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.schedule.TrinityMinimumSeedSchedule;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.schedule.TrinityMinimumSeedScheduler;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityPlanQuality;
import com.fish_dan_.data_energistics.util.FastUtilCollections;

import appeng.api.stacks.AEKey;

import net.minecraft.network.chat.Component;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectSet;

import java.math.BigInteger;
import java.util.Optional;

/**
 * Converts one conservation-feasible firing vector into an executable candidate with the true lexicographic cost.
 * <p>
 * Evaluates exact firing points without owning branch queues or firing-domain decisions.
 */
public final class TrinityJointCandidateEvaluator {

    /**
     * @return stateless exact evaluator shared by all firing boxes
     */
    public static TrinityJointCandidateEvaluator create() {
        return new TrinityJointCandidateEvaluator(
                TrinityExactConservationVerifier.create(),
                TrinityCompressedScheduler.create(),
                TrinityMinimumSeedScheduler.create());
    }

    private static final String MIP_TIMEOUT_KEY = "gui.data_energistics.trinity_planning.mip.timeout";
    private static final String NO_ORDER_KEY = "gui.data_energistics.trinity_planning.mip.no_executable_order";
    private static final String SEARCH_LIMIT_KEY = "gui.data_energistics.trinity_planning.mip.schedule_search_limit";

    private final TrinityExactConservationVerifier conservationVerifier;
    private final TrinityCompressedScheduler compressedScheduler;
    private final TrinityMinimumSeedScheduler seedScheduler;

    TrinityJointCandidateEvaluator(
                                   TrinityExactConservationVerifier conservationVerifier,
                                   TrinityCompressedScheduler compressedScheduler,
                                   TrinityMinimumSeedScheduler seedScheduler) {
        this.conservationVerifier = conservationVerifier;
        this.compressedScheduler = compressedScheduler;
        this.seedScheduler = seedScheduler;
    }

    /**
     * Schedules and verifies one canonical MIP point. A no-order diagnostic rejects only this point; search limits,
     * cancellation and timeout remain terminal for the bounded parent search.
     */
    public TrinityAlgorithmResult<TrinityJointCandidateEvaluation> evaluate(
                                                                            ObjectList<TrinityPatternVariant> variants,
                                                                            ObjectSet<AEKey> internalKeys,
                                                                            TrinityCycleDemand demand,
                                                                            Object2ObjectMap<AEKey, BigInteger> available,
                                                                            ObjectSet<AEKey> producibleInputs,
                                                                            TrinityCycleFeasibilitySolution solution,
                                                                            int maxScheduleStates,
                                                                            int solverPasses,
                                                                            long solverNanos,
                                                                            TrinityPlanningControl control) {
        if (variants.isEmpty() || internalKeys.isEmpty() || maxScheduleStates <= 0 || solverPasses <= 0 || solverNanos < 0L) {
            throw new IllegalArgumentException("A Trinity joint candidate evaluation request is incomplete");
        }
        ObjectList<TrinityPatternVariant> orderedVariants = variants.stream().sorted().collect(ObjectArrayList.toList());
        ObjectSet<AEKey> externalKeys = externalReserveKeys(orderedVariants, internalKeys, demand);
        CandidateAccounting accounting = accountCandidate(solution.firings(), internalKeys, demand)
                .orElseThrow(() -> new IllegalStateException(
                        "An exact Trinity MIP solution failed its demand accounting"));
        if (exceedsAvailable(accounting.externalInputs(), available, producibleInputs) ||
                exceedsAvailable(accounting.requiredModelSeed(), available, producibleInputs)) {
            throw new IllegalStateException("An exact Trinity MIP candidate exceeded its input domain");
        }

        Object2ObjectMap<AEKey, BigInteger> maximumInputs = candidateInputBounds(
                solution.firings(),
                externalKeys,
                internalKeys,
                accounting,
                available,
                producibleInputs);
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> minimumInputs = new Object2ObjectLinkedOpenHashMap<>(accounting.externalInputs());
        accounting.requiredModelSeed().forEach(
                (key, amount) -> minimumInputs.merge(key, amount, BigInteger::add));
        TrinityAlgorithmResult<TrinityMinimumSeedSchedule> scheduledResult = solution.quality() == TrinityPlanQuality.VERIFIED_FEASIBLE ?
                findFirstExecutableInputs(solution.firings(), externalKeys, internalKeys, minimumInputs,
                        maximumInputs, maxScheduleStates, control) :
                findExecutableInputs(
                        solution.firings(),
                        externalKeys,
                        internalKeys,
                        minimumInputs,
                        maximumInputs,
                        solution.seedTotal(),
                        maxScheduleStates,
                        control);
        if (!scheduledResult.successful()) {
            return TrinityAlgorithmResult.failure(scheduledResult.diagnostic());
        }
        TrinityMinimumSeedSchedule scheduled = scheduledResult.value();
        requireExternalInputs(scheduled.externalInputs(), accounting.externalInputs());

        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> initialInputs = new Object2ObjectLinkedOpenHashMap<>(scheduled.externalInputs());
        scheduled.minimumSeed().forEach((key, amount) -> initialInputs.merge(key, amount, BigInteger::add));
        TrinityAlgorithmResult<Object2ObjectMap<AEKey, BigInteger>> exact = this.conservationVerifier.verify(
                orderedVariants,
                solution.firings(),
                initialInputs,
                finiteInputUpperBounds(orderedVariants, internalKeys, demand, available, producibleInputs),
                demand.finalBalanceLowerBounds(),
                demand.requiredNetChangeLowerBounds());
        if (!exact.successful()) {
            TrinityPlanningDiagnostic diagnostic = exact.diagnostic();
            Object2ObjectLinkedOpenHashMap<String, String> metadata = new Object2ObjectLinkedOpenHashMap<>(diagnostic.metadata());
            metadata.put("states", Integer.toString(scheduled.schedule().statesVisited()));
            return TrinityAlgorithmResult.failure(new TrinityPlanningDiagnostic(
                    diagnostic.code(),
                    diagnostic.message(),
                    metadata,
                    diagnostic.detail()));
        }

        Object2ObjectMap<AEKey, BigInteger> finalBalances = addSigned(initialInputs, accounting.netChange());
        TrinityCompressedSchedule adjustedSchedule = new TrinityCompressedSchedule(
                scheduled.schedule().batches(),
                finalBalances,
                scheduled.schedule().statesVisited());
        TrinityJointCyclePlan plan = new TrinityJointCyclePlan(
                solution.firings(),
                scheduled.externalInputs(),
                scheduled.minimumSeed(),
                initialInputs,
                accounting.netChange(),
                adjustedSchedule,
                adjustedSchedule.statesVisited(),
                solverPasses,
                solverNanos,
                solution.quality());
        TrinityLexicographicObjective objective = new TrinityLexicographicObjective(
                sum(plan.externalInputs()),
                sum(plan.minimumSeed()),
                sum(plan.firings()),
                TrinityFiringVector.from(orderedVariants, plan.firings()));
        return TrinityAlgorithmResult.success(new TrinityJointCandidateEvaluation(
                plan,
                objective,
                adjustedSchedule.statesVisited()));
    }

    private TrinityAlgorithmResult<TrinityMinimumSeedSchedule> findFirstExecutableInputs(
                                                                                         Object2ObjectMap<TrinityPatternVariant, BigInteger> firings, ObjectSet<AEKey> externalKeys, ObjectSet<AEKey> internalKeys,
                                                                                         Object2ObjectMap<AEKey, BigInteger> minimumInputs, Object2ObjectMap<AEKey, BigInteger> maximumInputs,
                                                                                         int maxStates, TrinityPlanningControl control) {
        TrinityAlgorithmResult<TrinityCompressedSchedule> direct = this.compressedScheduler.schedule(
                firings, minimumInputs, maxStates, control);
        if (direct.successful()) {
            return TrinityAlgorithmResult.success(new TrinityMinimumSeedSchedule(
                    amountsFor(minimumInputs, externalKeys), amountsFor(minimumInputs, internalKeys), direct.value()));
        }
        int directStates = diagnosticStates(direct.diagnostic());
        if (direct.diagnostic().code() != TrinityPlanningDiagnosticCode.NO_EXECUTABLE_ORDER || maximumInputs.equals(minimumInputs)) {
            return TrinityAlgorithmResult.failure(normalizeFailure(direct.diagnostic(), directStates, maxStates));
        }
        int remaining = maxStates - directStates;
        if (remaining <= 0) return searchLimit(maxStates, directStates);
        TrinityAlgorithmResult<TrinityCompressedSchedule> searched = this.compressedScheduler.schedule(
                firings, maximumInputs, remaining, control);
        if (!searched.successful()) {
            return TrinityAlgorithmResult.failure(normalizeFailure(searched.diagnostic(),
                    Math.addExact(directStates, diagnosticStates(searched.diagnostic())), maxStates));
        }
        TrinityCompressedSchedule schedule = searched.value();
        // This scheduler returns flat batches. Tighten the chosen order's inputs, not the best inputs over all orders.
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> required = new Object2ObjectLinkedOpenHashMap<>(TrinityCycleSeedRequirement.minimumInputs(schedule.batches()));
        minimumInputs.forEach((key, amount) -> required.merge(key, amount, BigInteger::max));
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> finalBalances = new Object2ObjectLinkedOpenHashMap<>(schedule.finalBalances());
        maximumInputs.forEach((key, amount) -> finalBalances.merge(
                key, required.getOrDefault(key, BigInteger.ZERO).subtract(amount), BigInteger::add));
        finalBalances.entrySet().removeIf(entry -> entry.getValue().signum() == 0);
        int states = Math.addExact(directStates, schedule.statesVisited());
        return TrinityAlgorithmResult.success(new TrinityMinimumSeedSchedule(
                amountsFor(required, externalKeys), amountsFor(required, internalKeys),
                new TrinityCompressedSchedule(schedule.batches(), finalBalances, states)));
    }

    private TrinityAlgorithmResult<TrinityMinimumSeedSchedule> findExecutableInputs(
                                                                                    Object2ObjectMap<TrinityPatternVariant, BigInteger> firings,
                                                                                    ObjectSet<AEKey> externalKeys,
                                                                                    ObjectSet<AEKey> internalKeys,
                                                                                    Object2ObjectMap<AEKey, BigInteger> minimumInputs,
                                                                                    Object2ObjectMap<AEKey, BigInteger> maximumInputs,
                                                                                    BigInteger seedLowerBound,
                                                                                    int maxStates,
                                                                                    TrinityPlanningControl control) {
        if (seedLowerBound.signum() < 0) {
            throw new IllegalArgumentException("A Trinity seed lower bound cannot be negative");
        }
        int directStates = 0;
        // Conservation already proves a smaller total seed cannot execute, without constraining its key distribution.
        if (sum(amountsFor(minimumInputs, internalKeys)).compareTo(seedLowerBound) >= 0) {
            TrinityAlgorithmResult<TrinityCompressedSchedule> direct = this.compressedScheduler.schedule(
                    firings,
                    minimumInputs,
                    maxStates,
                    control);
            if (direct.successful()) {
                return TrinityAlgorithmResult.success(new TrinityMinimumSeedSchedule(
                        amountsFor(minimumInputs, externalKeys),
                        amountsFor(minimumInputs, internalKeys),
                        direct.value()));
            }
            directStates = diagnosticStates(direct.diagnostic());
            if (direct.diagnostic().code() != TrinityPlanningDiagnosticCode.NO_EXECUTABLE_ORDER) {
                return TrinityAlgorithmResult.failure(normalizeFailure(direct.diagnostic(), directStates, maxStates));
            }
        }
        int remaining = maxStates - directStates;
        if (remaining <= 0) {
            return searchLimit(maxStates, directStates);
        }

        TrinityAlgorithmResult<TrinityMinimumSeedSchedule> searched = this.seedScheduler.find(
                firings,
                externalKeys,
                internalKeys,
                minimumInputs,
                maximumInputs,
                remaining,
                control);
        if (searched.successful()) {
            int totalStates = Math.addExact(directStates, searched.value().schedule().statesVisited());
            TrinityCompressedSchedule schedule = searched.value().schedule();
            return TrinityAlgorithmResult.success(new TrinityMinimumSeedSchedule(
                    searched.value().externalInputs(),
                    searched.value().minimumSeed(),
                    new TrinityCompressedSchedule(schedule.batches(), schedule.finalBalances(), totalStates)));
        }
        int totalStates = Math.addExact(directStates, diagnosticStates(searched.diagnostic()));
        if (searched.diagnostic().code() == TrinityPlanningDiagnosticCode.NO_EXECUTABLE_ORDER) {
            return failure(
                    TrinityPlanningDiagnosticCode.NO_EXECUTABLE_ORDER,
                    NO_ORDER_KEY,
                    FastUtilCollections.mapOf("states", Integer.toString(totalStates)));
        }
        return TrinityAlgorithmResult.failure(normalizeFailure(searched.diagnostic(), totalStates, maxStates));
    }

    private static TrinityPlanningDiagnostic normalizeFailure(
                                                              TrinityPlanningDiagnostic diagnostic,
                                                              int states,
                                                              int limit) {
        if (diagnostic.code() == TrinityPlanningDiagnosticCode.ORDER_SEARCH_LIMIT &&
                "timeout".equals(diagnostic.metadata().get("reason"))) {
            return new TrinityPlanningDiagnostic(
                    TrinityPlanningDiagnosticCode.MIP_TIMEOUT,
                    Component.translatable(MIP_TIMEOUT_KEY),
                    FastUtilCollections.mapOf("states", Integer.toString(states)));
        }
        if (diagnostic.code() == TrinityPlanningDiagnosticCode.ORDER_SEARCH_LIMIT) {
            return new TrinityPlanningDiagnostic(
                    TrinityPlanningDiagnosticCode.ORDER_SEARCH_LIMIT,
                    Component.translatable(SEARCH_LIMIT_KEY),
                    FastUtilCollections.mapOf("limit", Integer.toString(limit), "states", Integer.toString(states)));
        }
        Object2ObjectLinkedOpenHashMap<String, String> metadata = new Object2ObjectLinkedOpenHashMap<>(diagnostic.metadata());
        metadata.put("states", Integer.toString(states));
        return new TrinityPlanningDiagnostic(diagnostic.code(), diagnostic.message(), metadata, diagnostic.detail());
    }

    private static TrinityAlgorithmResult<TrinityMinimumSeedSchedule> searchLimit(int limit, int states) {
        return failure(
                TrinityPlanningDiagnosticCode.ORDER_SEARCH_LIMIT,
                SEARCH_LIMIT_KEY,
                FastUtilCollections.mapOf("limit", Integer.toString(limit), "states", Integer.toString(states)));
    }

    private static Optional<CandidateAccounting> accountCandidate(
                                                                  Object2ObjectMap<TrinityPatternVariant, BigInteger> firings,
                                                                  ObjectSet<AEKey> internalKeys,
                                                                  TrinityCycleDemand demand) {
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> net = new Object2ObjectLinkedOpenHashMap<>();
        firings.forEach((variant, count) -> variant.netChange().forEach(
                (key, amount) -> net.merge(key, amount.multiply(count), BigInteger::add)));
        net.entrySet().removeIf(entry -> entry.getValue().signum() == 0);
        // Negative internal net change is paid for by modelSeed below, then checked against stock and
        // replayed by the scheduler. Only an explicit net-new demand constrains the sign independently.
        for (Object2ObjectMap.Entry<AEKey, BigInteger> bound : demand.requiredNetChangeLowerBounds().object2ObjectEntrySet()) {
            if (net.getOrDefault(bound.getKey(), BigInteger.ZERO).compareTo(bound.getValue()) < 0) {
                return Optional.empty();
            }
        }
        ObjectLinkedOpenHashSet<AEKey> keys = new ObjectLinkedOpenHashSet<>(net.keySet());
        keys.addAll(internalKeys);
        keys.addAll(demand.finalBalanceLowerBounds().keySet());
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> external = new Object2ObjectLinkedOpenHashMap<>();
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> modelSeed = new Object2ObjectLinkedOpenHashMap<>();
        for (AEKey key : keys) {
            BigInteger finalLower = demand.finalBalanceLowerBounds().getOrDefault(key, BigInteger.ZERO);
            BigInteger required = finalLower.subtract(net.getOrDefault(key, BigInteger.ZERO)).max(BigInteger.ZERO);
            if (required.signum() > 0) {
                if (internalKeys.contains(key)) {
                    modelSeed.put(key, required);
                } else {
                    external.put(key, required);
                }
            }
        }
        return Optional.of(new CandidateAccounting(
                FastUtilCollections.immutableMap(external),
                FastUtilCollections.immutableMap(modelSeed),
                FastUtilCollections.immutableMap(net)));
    }

    private static Object2ObjectMap<AEKey, BigInteger> candidateInputBounds(
                                                                            Object2ObjectMap<TrinityPatternVariant, BigInteger> firings,
                                                                            ObjectSet<AEKey> externalKeys,
                                                                            ObjectSet<AEKey> internalKeys,
                                                                            CandidateAccounting accounting,
                                                                            Object2ObjectMap<AEKey, BigInteger> available,
                                                                            ObjectSet<AEKey> producibleInputs) {
        ObjectLinkedOpenHashSet<AEKey> injectableKeys = new ObjectLinkedOpenHashSet<>(externalKeys);
        firings.keySet().forEach(variant -> variant.inputs().keySet().stream()
                .filter(internalKeys::contains)
                .forEach(injectableKeys::add));
        injectableKeys.addAll(accounting.requiredModelSeed().keySet());

        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> totalConsumption = new Object2ObjectLinkedOpenHashMap<>();
        firings.forEach((variant, count) -> variant.inputs().forEach(
                (key, amount) -> totalConsumption.merge(key, amount.multiply(count), BigInteger::add)));
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> bounds = new Object2ObjectLinkedOpenHashMap<>();
        for (AEKey key : injectableKeys) {
            BigInteger bound = available.getOrDefault(key, BigInteger.ZERO);
            if (producibleInputs.contains(key)) {
                bound = bound.max(totalConsumption.getOrDefault(key, BigInteger.ZERO));
                bound = bound.max(accounting.externalInputs().getOrDefault(key, BigInteger.ZERO));
                bound = bound.max(accounting.requiredModelSeed().getOrDefault(key, BigInteger.ZERO));
            }
            bounds.put(key, bound);
        }
        return FastUtilCollections.immutableMap(bounds);
    }

    private static Object2ObjectMap<AEKey, BigInteger> finiteInputUpperBounds(
                                                                              ObjectList<TrinityPatternVariant> variants,
                                                                              ObjectSet<AEKey> internalKeys,
                                                                              TrinityCycleDemand demand,
                                                                              Object2ObjectMap<AEKey, BigInteger> available,
                                                                              ObjectSet<AEKey> producibleInputs) {
        ObjectLinkedOpenHashSet<AEKey> inputKeys = new ObjectLinkedOpenHashSet<>(internalKeys);
        variants.forEach(variant -> inputKeys.addAll(variant.inputs().keySet()));
        inputKeys.addAll(demand.finalBalanceLowerBounds().keySet());
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> bounds = new Object2ObjectLinkedOpenHashMap<>();
        inputKeys.stream()
                .filter(key -> !producibleInputs.contains(key))
                .forEach(key -> bounds.put(key, available.getOrDefault(key, BigInteger.ZERO)));
        return FastUtilCollections.immutableMap(bounds);
    }

    private static ObjectSet<AEKey> externalReserveKeys(
                                                        ObjectList<TrinityPatternVariant> variants,
                                                        ObjectSet<AEKey> internalKeys,
                                                        TrinityCycleDemand demand) {
        ObjectLinkedOpenHashSet<AEKey> externalKeys = new ObjectLinkedOpenHashSet<>();
        variants.forEach(variant -> variant.inputs().keySet().stream()
                .filter(key -> !internalKeys.contains(key))
                .forEach(externalKeys::add));
        demand.finalBalanceLowerBounds().keySet().stream()
                .filter(key -> !internalKeys.contains(key))
                .forEach(externalKeys::add);
        return FastUtilCollections.immutableSet(externalKeys);
    }

    private static Object2ObjectMap<AEKey, BigInteger> amountsFor(
                                                                  Object2ObjectMap<AEKey, BigInteger> amounts,
                                                                  ObjectSet<AEKey> keys) {
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> selected = new Object2ObjectLinkedOpenHashMap<>();
        keys.forEach(key -> {
            BigInteger amount = amounts.getOrDefault(key, BigInteger.ZERO);
            if (amount.signum() > 0) {
                selected.put(key, amount);
            }
        });
        return FastUtilCollections.immutableMap(selected);
    }

    private static void requireExternalInputs(
                                              Object2ObjectMap<AEKey, BigInteger> actual,
                                              Object2ObjectMap<AEKey, BigInteger> required) {
        if (required.entrySet().stream().anyMatch(entry -> actual.getOrDefault(entry.getKey(), BigInteger.ZERO).compareTo(entry.getValue()) < 0)) {
            throw new IllegalStateException("An exact Trinity schedule lost required external input");
        }
    }

    private static boolean exceedsAvailable(
                                            Object2ObjectMap<AEKey, BigInteger> required,
                                            Object2ObjectMap<AEKey, BigInteger> available,
                                            ObjectSet<AEKey> producibleInputs) {
        return required.entrySet().stream().anyMatch(entry -> !producibleInputs.contains(entry.getKey()) &&
                available.getOrDefault(entry.getKey(), BigInteger.ZERO).compareTo(entry.getValue()) < 0);
    }

    private static Object2ObjectMap<AEKey, BigInteger> addSigned(
                                                                 Object2ObjectMap<AEKey, BigInteger> initial,
                                                                 Object2ObjectMap<AEKey, BigInteger> change) {
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> result = new Object2ObjectLinkedOpenHashMap<>(initial);
        change.forEach((key, amount) -> result.merge(key, amount, BigInteger::add));
        if (result.values().stream().anyMatch(amount -> amount.signum() < 0)) {
            throw new IllegalStateException("An exact Trinity joint cycle candidate has a negative final balance");
        }
        result.entrySet().removeIf(entry -> entry.getValue().signum() == 0);
        return FastUtilCollections.immutableMap(result);
    }

    private static int diagnosticStates(TrinityPlanningDiagnostic diagnostic) {
        String encoded = diagnostic.metadata().get("states");
        if (encoded == null) {
            throw new IllegalStateException("A Trinity schedule diagnostic must report visited states");
        }
        try {
            int states = Integer.parseInt(encoded);
            if (states < 0) {
                throw new IllegalStateException("Trinity schedule diagnostic states cannot be negative");
            }
            return states;
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("Trinity schedule diagnostic states must be an integer", exception);
        }
    }

    private static BigInteger sum(Object2ObjectMap<?, BigInteger> amounts) {
        return amounts.values().stream().reduce(BigInteger.ZERO, BigInteger::add);
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

    private record CandidateAccounting(
                                       Object2ObjectMap<AEKey, BigInteger> externalInputs,
                                       Object2ObjectMap<AEKey, BigInteger> requiredModelSeed,
                                       Object2ObjectMap<AEKey, BigInteger> netChange) {}
}
