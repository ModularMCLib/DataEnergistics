package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.dag.optimization;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.CraftingQuantityMode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnostic;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnosticCode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningControl;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningMode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.dag.TrinityAcyclicPlan;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.schedule.TrinityVariantFiring;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.topology.TrinityCraftingTopology;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.topology.TrinityStronglyConnectedComponent;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternIdentity;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.inventory.TrinityPlanningInventory;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityPlanQuality;
import com.fish_dan_.data_energistics.util.AmountMath;
import com.fish_dan_.data_energistics.util.FastUtilCollections;

import appeng.api.stacks.AEKey;

import net.minecraft.network.chat.Component;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.jspecify.annotations.Nullable;

import java.math.BigInteger;
import java.util.Comparator;
import java.util.Optional;

/**
 * Proves when first-level acyclic competition regions are independent, solves them separately, and exactly verifies
 * their merged execution. Any uncertainty is represented as no attempt so the caller can retain whole-graph behavior.
 */
public final class TrinityAcyclicCompetitionPlanner {

    public static TrinityAcyclicCompetitionPlanner create(TrinityAcyclicRouteOptimizer routeOptimizer) {
        return new TrinityAcyclicCompetitionPlanner(routeOptimizer);
    }

    private final TrinityAcyclicRouteOptimizer routeOptimizer;

    private TrinityAcyclicCompetitionPlanner(TrinityAcyclicRouteOptimizer routeOptimizer) {
        if (routeOptimizer == null) {
            throw new IllegalArgumentException("A Trinity competition planner requires a route optimizer");
        }
        this.routeOptimizer = routeOptimizer;
    }

    /**
     * Attempts conservative partitioning. Once local solving starts, failures use the reserved whole-graph budget and
     * return that optimizer result so the caller performs shortage diagnosis exactly once.
     */
    public Optional<Attempt> plan(
                                  TrinityCraftingTopology topology,
                                  ObjectList<TrinityPatternVariant> planningVariants,
                                  Object2ObjectMap<AEKey, ObjectList<TrinityPatternVariant>> producers,
                                  AEKey target,
                                  BigInteger requestedAmount,
                                  CraftingQuantityMode quantityMode,
                                  TrinityPlanningInventory available,
                                  int maxSearchStates,
                                  TrinityPlanningMode mode,
                                  TrinityPlanningControl control) {
        return plan(
                topology,
                planningVariants,
                producers,
                target,
                requestedAmount,
                quantityMode,
                available,
                ObjectSet.of(),
                maxSearchStates,
                mode,
                control);
    }

    /** Uses cached route identities only as local exact-feasibility incumbents. */
    public Optional<Attempt> plan(
                                  TrinityCraftingTopology topology,
                                  ObjectList<TrinityPatternVariant> planningVariants,
                                  Object2ObjectMap<AEKey, ObjectList<TrinityPatternVariant>> producers,
                                  AEKey target,
                                  BigInteger requestedAmount,
                                  CraftingQuantityMode quantityMode,
                                  TrinityPlanningInventory available,
                                  ObjectSet<TrinityPatternIdentity> routeHint,
                                  int maxSearchStates,
                                  TrinityPlanningMode mode,
                                  TrinityPlanningControl control) {
        Preparation preparation = prepare(
                topology,
                producers,
                target,
                requestedAmount,
                quantityMode,
                available);
        if (preparation == null || preparation.frontiers().size() < 2) {
            return Optional.empty();
        }
        ObjectList<CompetitionRegion> regions = regions(preparation.frontiers(), producers);
        if (!provablyIndependent(regions, preparation)) {
            return Optional.empty();
        }

        int wholeGraphBudget = routePassUpperBound(planningVariants.size(), !routeHint.isEmpty());
        long localBudget = regions.stream()
                .mapToLong(region -> routePassUpperBound(
                        region.variants().size(),
                        region.patterns().stream().anyMatch(routeHint::contains)))
                .sum();
        if (localBudget + wholeGraphBudget + 1L > maxSearchStates) {
            return Optional.empty();
        }

        Object2ObjectLinkedOpenHashMap<TrinityPatternVariant, BigInteger> firings = new Object2ObjectLinkedOpenHashMap<>(
                preparation.deterministicFirings());
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> externalInputs = new Object2ObjectLinkedOpenHashMap<>(preparation.reservedInputs());
        TrinityPlanQuality quality = TrinityPlanQuality.PROVED_OPTIMAL;
        int states = 0;
        for (CompetitionRegion region : regions) {
            if (control.cancellationRequested()) {
                return Optional.of(new Attempt(cancelled(), wholeGraphBudget));
            }
            TrinityAlgorithmResult<TrinityAcyclicPlan> local = this.routeOptimizer.optimize(
                    topology,
                    region.variants(),
                    region.target(),
                    region.amount(),
                    CraftingQuantityMode.NET_NEW,
                    projectInventory(preparation.remainingInventory(), region.touchedKeys()),
                    localHint(routeHint, region.patterns()),
                    routePassUpperBound(
                            region.variants().size(),
                            region.patterns().stream().anyMatch(routeHint::contains)),
                    mode,
                    control);
            if (!local.successful()) {
                if (local.diagnostic().code() == TrinityPlanningDiagnosticCode.CALCULATION_CANCELLED) {
                    return Optional.of(new Attempt(local, wholeGraphBudget));
                }
                return Optional.of(new Attempt(optimizeWholeGraph(
                        topology,
                        planningVariants,
                        target,
                        requestedAmount,
                        quantityMode,
                        available,
                        routeHint,
                        wholeGraphBudget,
                        mode,
                        control), wholeGraphBudget));
            }
            local.value().firings().forEach((variant, count) -> firings.merge(variant, count, BigInteger::add));
            local.value().externalInputs().forEach(
                    (key, amount) -> externalInputs.merge(key, amount, BigInteger::add));
            quality = quality.combine(local.value().quality());
            states = Math.addExact(states, local.value().statesVisited());
        }

        TrinityAcyclicPlan combined = verifyCombined(
                topology,
                planningVariants,
                target,
                requestedAmount,
                quantityMode,
                available,
                firings,
                externalInputs,
                states,
                quality);
        if (combined == null) {
            return Optional.of(new Attempt(optimizeWholeGraph(
                    topology,
                    planningVariants,
                    target,
                    requestedAmount,
                    quantityMode,
                    available,
                    routeHint,
                    wholeGraphBudget,
                    mode,
                    control), wholeGraphBudget));
        }
        if (control.cancellationRequested()) {
            return Optional.of(new Attempt(cancelled(), wholeGraphBudget));
        }
        return Optional.of(new Attempt(TrinityAlgorithmResult.success(combined), wholeGraphBudget));
    }

    private TrinityAlgorithmResult<TrinityAcyclicPlan> optimizeWholeGraph(
                                                                          TrinityCraftingTopology topology,
                                                                          ObjectList<TrinityPatternVariant> variants,
                                                                          AEKey target,
                                                                          BigInteger requestedAmount,
                                                                          CraftingQuantityMode quantityMode,
                                                                          TrinityPlanningInventory available,
                                                                          ObjectSet<TrinityPatternIdentity> routeHint,
                                                                          int maxSearchStates,
                                                                          TrinityPlanningMode mode,
                                                                          TrinityPlanningControl control) {
        return this.routeOptimizer.optimize(
                topology,
                variants,
                target,
                requestedAmount,
                quantityMode,
                available,
                routeHint,
                maxSearchStates,
                mode,
                control);
    }

    private static @Nullable Preparation prepare(
                                                 TrinityCraftingTopology topology,
                                                 Object2ObjectMap<AEKey, ObjectList<TrinityPatternVariant>> producers,
                                                 AEKey target,
                                                 BigInteger requestedAmount,
                                                 CraftingQuantityMode quantityMode,
                                                 TrinityPlanningInventory available) {
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> finiteInventory = new Object2ObjectLinkedOpenHashMap<>(
                available.finiteAmounts());
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> need = new Object2ObjectLinkedOpenHashMap<>();
        merge(need, target, requestedAmount);
        Object2ObjectLinkedOpenHashMap<TrinityPatternVariant, BigInteger> deterministicFirings = new Object2ObjectLinkedOpenHashMap<>();
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> reservedInputs = new Object2ObjectLinkedOpenHashMap<>();
        Object2ObjectLinkedOpenHashMap<AEKey, FrontierDemand> frontiers = new Object2ObjectLinkedOpenHashMap<>();
        ObjectLinkedOpenHashSet<AEKey> deterministicTouchedKeys = new ObjectLinkedOpenHashSet<>();
        ObjectLinkedOpenHashSet<TrinityPatternIdentity> deterministicPatterns = new ObjectLinkedOpenHashSet<>();

        IntList componentOrder = topology.topologicalOrder();
        for (int position = componentOrder.size() - 1; position >= 0; position--) {
            TrinityStronglyConnectedComponent component = topology.components().get(componentOrder.getInt(position));
            for (AEKey key : component.keys()) {
                BigInteger required = need.getOrDefault(key, BigInteger.ZERO);
                boolean forceFinalTotalProduction = key.equals(target) &&
                        quantityMode == CraftingQuantityMode.FINAL_TOTAL;
                if (required.signum() <= 0 && !forceFinalTotalProduction) {
                    continue;
                }
                boolean unlimited = available.unlimited(key);
                BigInteger positiveRequired = required.max(BigInteger.ZERO);
                BigInteger availableAmount = unlimited ?
                        positiveRequired : finiteInventory.getOrDefault(key, BigInteger.ZERO);
                BigInteger reserved = key.equals(target) && quantityMode == CraftingQuantityMode.NET_NEW ?
                        BigInteger.ZERO : positiveRequired.min(availableAmount);
                if (reserved.signum() > 0) {
                    reservedInputs.merge(key, reserved, BigInteger::add);
                    if (!unlimited) {
                        finiteInventory.put(key, availableAmount.subtract(reserved));
                    }
                    merge(need, key, reserved.negate());
                    deterministicTouchedKeys.add(key);
                }
                BigInteger missing = need.getOrDefault(key, BigInteger.ZERO).max(BigInteger.ZERO);
                if (missing.signum() <= 0 && !forceFinalTotalProduction) {
                    continue;
                }
                ObjectList<TrinityPatternVariant> candidates = producers.getOrDefault(key, ObjectList.of());
                if (candidates.isEmpty()) {
                    return null;
                }
                if (candidates.size() > 1 || candidates.stream().anyMatch(variant -> variant.dependencyOutputs().size() > 1)) {
                    frontiers.put(key, new FrontierDemand(key, missing.signum() > 0 ? missing : BigInteger.ONE));
                    continue;
                }

                TrinityPatternVariant selected = candidates.getFirst();
                BigInteger count = missing.signum() > 0 ?
                        AmountMath.ceilDivideNonNegative(missing, selected.dependencyOutputs().get(key)) : BigInteger.ONE;
                deterministicFirings.merge(selected, count, BigInteger::add);
                deterministicPatterns.add(selected.patternIdentity());
                deterministicTouchedKeys.addAll(selected.inputs().keySet());
                deterministicTouchedKeys.addAll(selected.dependencyOutputs().keySet());
                selected.inputs().forEach((input, amount) -> merge(need, input, amount.multiply(count)));
                selected.outputs().forEach((output, amount) -> merge(need, output, amount.multiply(count).negate()));
            }
        }
        return new Preparation(
                FastUtilCollections.immutableList(frontiers.values()),
                deterministicFirings,
                reservedInputs,
                new TrinityPlanningInventory(finiteInventory, available.unlimitedKeys()),
                FastUtilCollections.immutableSet(deterministicTouchedKeys),
                FastUtilCollections.immutableSet(deterministicPatterns));
    }

    private static ObjectList<CompetitionRegion> regions(
                                                         ObjectList<FrontierDemand> frontiers,
                                                         Object2ObjectMap<AEKey, ObjectList<TrinityPatternVariant>> producers) {
        ObjectArrayList<CompetitionRegion> regions = new ObjectArrayList<>(frontiers.size());
        for (FrontierDemand frontier : frontiers) {
            ObjectArrayList<AEKey> pending = new ObjectArrayList<>();
            ObjectLinkedOpenHashSet<AEKey> visitedKeys = new ObjectLinkedOpenHashSet<>();
            ObjectLinkedOpenHashSet<TrinityPatternVariant> regionVariants = new ObjectLinkedOpenHashSet<>();
            pending.add(frontier.key());
            for (int index = 0; index < pending.size(); index++) {
                AEKey key = pending.get(index);
                if (!visitedKeys.add(key)) {
                    continue;
                }
                for (TrinityPatternVariant producer : producers.getOrDefault(key, ObjectList.of())) {
                    if (regionVariants.add(producer)) {
                        pending.addAll(producer.inputs().keySet());
                    }
                }
            }
            ObjectArrayList<TrinityPatternVariant> ordered = new ObjectArrayList<>(regionVariants);
            ordered.sort(Comparator.naturalOrder());
            ObjectLinkedOpenHashSet<AEKey> touchedKeys = new ObjectLinkedOpenHashSet<>();
            ObjectLinkedOpenHashSet<TrinityPatternIdentity> patterns = new ObjectLinkedOpenHashSet<>();
            for (TrinityPatternVariant variant : ordered) {
                patterns.add(variant.patternIdentity());
                touchedKeys.addAll(variant.inputs().keySet());
                touchedKeys.addAll(variant.dependencyOutputs().keySet());
            }
            regions.add(new CompetitionRegion(
                    frontier.key(),
                    frontier.amount(),
                    FastUtilCollections.immutableList(ordered),
                    FastUtilCollections.immutableSet(touchedKeys),
                    FastUtilCollections.immutableSet(patterns)));
        }
        return FastUtilCollections.immutableList(regions);
    }

    private static boolean provablyIndependent(ObjectList<CompetitionRegion> regions, Preparation preparation) {
        boolean reservedCraftableSuffix = preparation.deterministicFirings().keySet().stream()
                .flatMap(variant -> variant.dependencyOutputs().keySet().stream())
                .anyMatch(preparation.reservedInputs()::containsKey);
        if (reservedCraftableSuffix) {
            return false;
        }
        ObjectLinkedOpenHashSet<AEKey> occupiedKeys = new ObjectLinkedOpenHashSet<>();
        ObjectLinkedOpenHashSet<TrinityPatternIdentity> occupiedPatterns = new ObjectLinkedOpenHashSet<>();
        for (CompetitionRegion region : regions) {
            if (region.variants().isEmpty() ||
                    preparation.reservedInputs().containsKey(region.target()) ||
                    region.patterns().stream().anyMatch(preparation.deterministicPatterns()::contains)) {
                return false;
            }
            for (AEKey key : region.touchedKeys()) {
                if (preparation.deterministicTouchedKeys().contains(key) && !key.equals(region.target())) {
                    return false;
                }
            }
            if (region.touchedKeys().stream().anyMatch(key -> !occupiedKeys.add(key)) ||
                    region.patterns().stream().anyMatch(pattern -> !occupiedPatterns.add(pattern))) {
                return false;
            }
        }
        return true;
    }

    private static @Nullable TrinityAcyclicPlan verifyCombined(
                                                               TrinityCraftingTopology topology,
                                                               ObjectList<TrinityPatternVariant> legalVariants,
                                                               AEKey target,
                                                               BigInteger requestedAmount,
                                                               CraftingQuantityMode quantityMode,
                                                               TrinityPlanningInventory available,
                                                               Object2ObjectMap<TrinityPatternVariant, BigInteger> firings,
                                                               Object2ObjectMap<AEKey, BigInteger> externalInputs,
                                                               int states,
                                                               TrinityPlanQuality quality) {
        ObjectSet<TrinityPatternVariant> legal = new ObjectOpenHashSet<>(legalVariants);
        if (firings.isEmpty() || firings.entrySet().stream().anyMatch(
                entry -> !legal.contains(entry.getKey()) || entry.getValue().signum() <= 0)) {
            return null;
        }
        for (Object2ObjectMap.Entry<AEKey, BigInteger> input : externalInputs.object2ObjectEntrySet()) {
            if (input.getValue().signum() <= 0 ||
                    !available.covers(input.getKey(), input.getValue()) ||
                    quantityMode == CraftingQuantityMode.NET_NEW && input.getKey().equals(target)) {
                return null;
            }
        }

        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> net = aggregateNetChange(firings);
        ObjectLinkedOpenHashSet<AEKey> balanceKeys = new ObjectLinkedOpenHashSet<>(net.keySet());
        balanceKeys.addAll(externalInputs.keySet());
        for (AEKey key : balanceKeys) {
            if (externalInputs.getOrDefault(key, BigInteger.ZERO)
                    .add(net.getOrDefault(key, BigInteger.ZERO)).signum() < 0) {
                return null;
            }
        }
        BigInteger targetNet = net.getOrDefault(target, BigInteger.ZERO);
        if (targetNet.signum() <= 0 ||
                quantityMode == CraftingQuantityMode.NET_NEW && targetNet.compareTo(requestedAmount) < 0 ||
                quantityMode == CraftingQuantityMode.FINAL_TOTAL &&
                        externalInputs.getOrDefault(target, BigInteger.ZERO)
                                .add(targetNet).compareTo(requestedAmount) < 0) {
            return null;
        }

        Int2IntMap positions = topologicalPositions(topology);
        ObjectArrayList<TrinityVariantFiring> executionOrder = new ObjectArrayList<>();
        firings.object2ObjectEntrySet().stream()
                .sorted(Comparator
                        .comparingInt((Object2ObjectMap.Entry<TrinityPatternVariant, BigInteger> entry) -> producerPosition(
                                topology,
                                positions,
                                entry.getKey()))
                        .thenComparing(Object2ObjectMap.Entry::getKey))
                .forEach(entry -> executionOrder.add(new TrinityVariantFiring(entry.getKey(), entry.getValue())));
        if (!executionPrefixNonNegative(executionOrder, externalInputs)) {
            return null;
        }
        Object2ObjectLinkedOpenHashMap<TrinityPatternVariant, BigInteger> orderedFirings = new Object2ObjectLinkedOpenHashMap<>();
        executionOrder.forEach(firing -> orderedFirings.put(firing.variant(), firing.count()));
        return new TrinityAcyclicPlan(
                executionOrder,
                orderedFirings,
                externalInputs,
                net,
                states,
                quality);
    }

    private static TrinityPlanningInventory projectInventory(
                                                             TrinityPlanningInventory inventory,
                                                             ObjectSet<AEKey> touchedKeys) {
        return inventory.project(touchedKeys);
    }

    private static boolean executionPrefixNonNegative(
                                                      ObjectList<TrinityVariantFiring> executionOrder,
                                                      Object2ObjectMap<AEKey, BigInteger> externalInputs) {
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> balance = new Object2ObjectLinkedOpenHashMap<>(externalInputs);
        for (TrinityVariantFiring firing : executionOrder) {
            for (Object2ObjectMap.Entry<AEKey, BigInteger> input : firing.variant().inputs().object2ObjectEntrySet()) {
                BigInteger required = input.getValue().multiply(firing.count());
                BigInteger present = balance.getOrDefault(input.getKey(), BigInteger.ZERO);
                if (present.compareTo(required) < 0) {
                    return false;
                }
                balance.put(input.getKey(), present.subtract(required));
            }
            firing.variant().outputs().forEach((key, amount) -> balance.merge(
                    key,
                    amount.multiply(firing.count()),
                    BigInteger::add));
        }
        return true;
    }

    private static Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> aggregateNetChange(
                                                                                        Object2ObjectMap<TrinityPatternVariant, BigInteger> firings) {
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> net = new Object2ObjectLinkedOpenHashMap<>();
        firings.forEach((variant, count) -> variant.netChange().forEach(
                (key, amount) -> net.merge(key, amount.multiply(count), BigInteger::add)));
        net.entrySet().removeIf(entry -> entry.getValue().signum() == 0);
        return net;
    }

    private static Int2IntMap topologicalPositions(TrinityCraftingTopology topology) {
        Int2IntOpenHashMap positions = new Int2IntOpenHashMap();
        for (int position = 0; position < topology.topologicalOrder().size(); position++) {
            positions.put(topology.topologicalOrder().getInt(position), position);
        }
        return positions;
    }

    private static int producerPosition(TrinityCraftingTopology topology,
                                        Int2IntMap positions,
                                        TrinityPatternVariant variant) {
        int earliestOutput = Integer.MAX_VALUE;
        for (AEKey output : variant.dependencyOutputs().keySet()) {
            if (topology.componentByKey().containsKey(output)) {
                int component = topology.componentByKey().getInt(output);
                earliestOutput = Math.min(earliestOutput, positions.get(component));
            }
        }
        return earliestOutput;
    }

    private static void merge(Object2ObjectMap<AEKey, BigInteger> amounts, AEKey key, BigInteger amount) {
        amounts.merge(key, amount, BigInteger::add);
    }

    private static ObjectSet<TrinityPatternIdentity> localHint(
                                                               ObjectSet<TrinityPatternIdentity> routeHint,
                                                               ObjectSet<TrinityPatternIdentity> regionPatterns) {
        ObjectOpenHashSet<TrinityPatternIdentity> selected = new ObjectOpenHashSet<>(routeHint);
        selected.retainAll(regionPatterns);
        return selected;
    }

    private static int routePassUpperBound(int variantCount, boolean hasHint) {
        return Math.addExact(Math.addExact(variantCount, 1), hasHint ? 1 : 0);
    }

    private static TrinityAlgorithmResult<TrinityAcyclicPlan> cancelled() {
        return TrinityAlgorithmResult.failure(new TrinityPlanningDiagnostic(
                TrinityPlanningDiagnosticCode.CALCULATION_CANCELLED,
                Component.translatable("gui.data_energistics.trinity_planning.diagnostic.cancelled"),
                FastUtilCollections.mapOf("phase", "dag_competition")));
    }

    /** Result from either a verified partition or its state-budgeted whole-graph fallback. */
    public record Attempt(TrinityAlgorithmResult<TrinityAcyclicPlan> result, int diagnosticBudget) {

        public Attempt {
            if (result == null || diagnosticBudget <= 0) {
                throw new IllegalArgumentException("A Trinity competition attempt requires a result and budget");
            }
        }
    }

    private record FrontierDemand(AEKey key, BigInteger amount) {}

    private record CompetitionRegion(
                                     AEKey target,
                                     BigInteger amount,
                                     ObjectList<TrinityPatternVariant> variants,
                                     ObjectSet<AEKey> touchedKeys,
                                     ObjectSet<TrinityPatternIdentity> patterns) {}

    private record Preparation(
                               ObjectList<FrontierDemand> frontiers,
                               Object2ObjectMap<TrinityPatternVariant, BigInteger> deterministicFirings,
                               Object2ObjectMap<AEKey, BigInteger> reservedInputs,
                               TrinityPlanningInventory remainingInventory,
                               ObjectSet<AEKey> deterministicTouchedKeys,
                               ObjectSet<TrinityPatternIdentity> deterministicPatterns) {}
}
