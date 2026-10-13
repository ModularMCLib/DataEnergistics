package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.orchestration.assembly;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnostic;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnosticCode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.selection.TrinityCycleSelection;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.dag.TrinityAcyclicPlan;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.orchestration.TrinityGraphPlanContext;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.orchestration.demand.TrinityGraphDemandSolution;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.schedule.TrinityVariantFiring;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.topology.TrinityCraftingTopology;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternIdentity;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityCraftingPlan;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityCycleRepeatBlock;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityPlanByteEstimateInput;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityPlanByteEstimator;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityPlanPatternFiring;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityPlanStage;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityPlanningStatistics;
import com.fish_dan_.data_energistics.util.FastUtilCollections;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;

import net.minecraft.network.chat.Component;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntLists;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSets;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;

import java.math.BigInteger;
import java.util.Comparator;

/**
 * Converts solved graph demands into compact execution stages and final immutable Trinity crafting plans.
 * <p>
 * Builds resource-safe independent stages and compressed repeat blocks from exact aggregate firing selections.
 */
public final class TrinityGraphPlanAssembler {

    /**
     * Creates a plan assembler using the shared conservative AE2 byte estimator.
     */
    public static TrinityGraphPlanAssembler create(TrinityPlanByteEstimator byteEstimator) {
        return new TrinityGraphPlanAssembler(byteEstimator);
    }

    private static final String INSUFFICIENT_INPUT_KEY = "gui.data_energistics.trinity_planning.diagnostic.insufficient_input";

    private final TrinityPlanByteEstimator byteEstimator;

    TrinityGraphPlanAssembler(TrinityPlanByteEstimator byteEstimator) {
        if (byteEstimator == null) {
            throw new IllegalArgumentException("A Trinity graph plan assembler requires a byte estimator");
        }
        this.byteEstimator = byteEstimator;
    }

    /**
     * Converts the dedicated DAG propagator result into the common plan payload.
     */
    public TrinityGraphPlanAssembly assembleAcyclic(TrinityAcyclicPlan acyclicPlan) {
        if (acyclicPlan == null) {
            throw new IllegalArgumentException("A Trinity acyclic plan assembly requires a solved plan");
        }
        ObjectArrayList<TrinityPlanStage> stages = new ObjectArrayList<>(acyclicPlan.executionOrder().size());
        IntArrayList stageOrder = new IntArrayList(acyclicPlan.executionOrder().size());
        Object2ObjectLinkedOpenHashMap<TrinityPatternIdentity, BigInteger> patternFirings = new Object2ObjectLinkedOpenHashMap<>();
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> stackRequests = new Object2ObjectLinkedOpenHashMap<>();
        for (TrinityVariantFiring firing : acyclicPlan.executionOrder()) {
            int stageIndex = stages.size();
            stages.add(stage(
                    stageIndex,
                    false,
                    firing.variant(),
                    firing.count()));
            stageOrder.add(stageIndex);
            mergePatternFiring(patternFirings, firing.variant(), firing.count());
            chargeStacks(stackRequests, firing.variant(), firing.count());
        }
        ObjectList<TrinityPlanStage> plannedStages = TrinityStageDependencyPlanner.plan(
                acyclicPlan.externalInputs(),
                stages,
                stageOrder,
                ObjectList.of());
        return new TrinityGraphPlanAssembly(
                acyclicPlan.externalInputs(),
                FastUtilCollections.immutableMap(patternFirings),
                plannedStages,
                IntLists.unmodifiable(new IntArrayList(stageOrder)),
                ObjectList.of(),
                FastUtilCollections.mapOf(),
                acyclicPlan.netChange(),
                FastUtilCollections.immutableMap(stackRequests),
                acyclicPlan.statesVisited(),
                0L,
                acyclicPlan.quality(),
                FastUtilCollections.mapOf(),
                FastUtilCollections.mapOf(),
                0);
    }

    /**
     * Converts aggregate acyclic firings and selected cycle blocks into the common plan payload.
     */
    public TrinityAlgorithmResult<TrinityGraphPlanAssembly> assembleDemand(
                                                                           AEKey target,
                                                                           TrinityCraftingTopology topology,
                                                                           TrinityGraphDemandSolution demandSolution) {
        if (target == null || topology == null || demandSolution == null) {
            throw new IllegalArgumentException("A Trinity aggregate plan assembly request is incomplete");
        }
        Int2IntMap topologicalPositions = topologicalPositions(topology);
        ObjectArrayList<OrderedUnit> units = new ObjectArrayList<>();
        demandSolution.acyclicFirings().forEach((variant, firing) -> units.add(new AcyclicUnit(
                firing.rank(),
                variant,
                firing.count())));
        for (int index = 0; index < demandSolution.cycleSolutions().size(); index++) {
            TrinityCycleSelection cycle = demandSolution.cycleSolutions().get(index);
            units.add(new CycleUnit(
                    Math.multiplyExact(topologicalPositions.get(cycle.componentIndex()), 2),
                    index,
                    cycle));
        }
        units.sort(Comparator
                .comparingInt(OrderedUnit::rank)
                .thenComparing(OrderedUnit::stableKey));

        ObjectArrayList<TrinityPlanStage> stages = new ObjectArrayList<>();
        IntArrayList stageOrder = new IntArrayList();
        ObjectArrayList<TrinityCycleRepeatBlock> repeatBlocks = new ObjectArrayList<>();
        Object2ObjectLinkedOpenHashMap<TrinityPatternIdentity, BigInteger> patternFirings = new Object2ObjectLinkedOpenHashMap<>();
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> netChange = new Object2ObjectLinkedOpenHashMap<>();
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> minimumSeed = new Object2ObjectLinkedOpenHashMap<>();
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> retainedSeed = new Object2ObjectLinkedOpenHashMap<>();
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> stackRequests = new Object2ObjectLinkedOpenHashMap<>();
        int seedRefinementPasses = 0;
        int repeatIndex = 0;

        for (OrderedUnit unit : units) {
            if (unit instanceof AcyclicUnit acyclic) {
                int stageIndex = stages.size();
                stages.add(stage(
                        stageIndex,
                        false,
                        acyclic.variant(),
                        acyclic.count()));
                stageOrder.add(stageIndex);
                mergePatternFiring(patternFirings, acyclic.variant(), acyclic.count());
                mergeScaled(netChange, acyclic.variant().netChange(), acyclic.count());
                chargeStacks(stackRequests, acyclic.variant(), acyclic.count());
                continue;
            }

            TrinityCycleSelection cycle = ((CycleUnit) unit).solution();
            appendOneTimeStages(
                    cycle.prefixOrder(),
                    stages,
                    stageOrder,
                    patternFirings,
                    stackRequests);
            IntArrayList blockStages = new IntArrayList();
            Object2ObjectMap<AEKey, BigInteger> repeatedNet = repeatedNetChange(cycle.localOrder(), cycle.repetitions());
            boolean productiveRepeat = cycle.hasProductiveRepeat(topology.components().get(cycle.componentIndex()).keys());
            // A structural SCC is not proof of amplification. Only an exact positive internal gain with every
            // internal balance preserved may form a compressed repeat block. Finite non-amplifying routes remain
            // ordinary sequential stages, where the dependency planner validates their actual material balance.
            if (!productiveRepeat && !cycle.repetitions().equals(BigInteger.ONE)) {
                throw new IllegalStateException("A finite Trinity route must be scheduled before stage assembly");
            }
            for (TrinityVariantFiring batch : cycle.localOrder()) {
                int stageIndex = stages.size();
                stages.add(stage(
                        stageIndex,
                        productiveRepeat,
                        batch.variant(),
                        batch.count()));
                stageOrder.add(stageIndex);
                blockStages.add(stageIndex);
                BigInteger totalCount = batch.count().multiply(cycle.repetitions());
                mergePatternFiring(patternFirings, batch.variant(), totalCount);
                chargeStacks(stackRequests, batch.variant(), totalCount);
            }
            if (productiveRepeat) {
                repeatBlocks.add(new TrinityCycleRepeatBlock(
                        repeatIndex++,
                        IntList.of(blockStages.toIntArray()),
                        cycle.repetitions(),
                        minimumBalances(cycle.localOrder()),
                        repeatedNet));
                cycle.minimumSeed().forEach((key, amount) -> minimumSeed.merge(key, amount, BigInteger::max));
            }
            appendOneTimeStages(
                    cycle.suffixOrder(),
                    stages,
                    stageOrder,
                    patternFirings,
                    stackRequests);
            cycle.retainedSeed().forEach((key, amount) -> retainedSeed.merge(key, amount, BigInteger::max));
            seedRefinementPasses = Math.addExact(seedRefinementPasses, cycle.seedRefinementPasses());
            mergeScaled(netChange, cycle.netChange(), BigInteger.ONE);
        }
        removeZeros(netChange);
        if (stages.isEmpty()) {
            return failure(
                    TrinityPlanningDiagnosticCode.INSUFFICIENT_INPUT,
                    INSUFFICIENT_INPUT_KEY,
                    FastUtilCollections.mapOf("target", target.toString()));
        }
        ObjectList<TrinityPlanStage> plannedStages = TrinityStageDependencyPlanner.plan(
                demandSolution.initialInputs(),
                stages,
                stageOrder,
                repeatBlocks);
        Object2ObjectMap<AEKey, BigInteger> retainedSeedFinal = terminalSeedBalances(
                demandSolution.initialInputs(),
                plannedStages,
                stageOrder,
                repeatBlocks,
                retainedSeed);
        Object2ObjectMap.Entry<AEKey, BigInteger> lostSeed = retainedSeed.object2ObjectEntrySet().stream()
                .filter(entry -> retainedSeedFinal.getOrDefault(entry.getKey(), BigInteger.ZERO)
                        .compareTo(entry.getValue()) < 0)
                .findFirst()
                .orElse(null);
        if (lostSeed != null) {
            return failure(
                    TrinityPlanningDiagnosticCode.INTERNAL_ERROR,
                    "gui.data_energistics.trinity_planning.diagnostic.internal_error",
                    FastUtilCollections.mapOf(
                            "phase", "terminal_seed_validation",
                            "key", lostSeed.getKey().toString(),
                            "required", lostSeed.getValue().toString()));
        }
        return TrinityAlgorithmResult.success(new TrinityGraphPlanAssembly(
                demandSolution.initialInputs(),
                FastUtilCollections.immutableMap(patternFirings),
                plannedStages,
                IntList.of(stageOrder.toIntArray()),
                FastUtilCollections.immutableList(repeatBlocks),
                FastUtilCollections.immutableMap(minimumSeed),
                FastUtilCollections.immutableMap(netChange),
                FastUtilCollections.immutableMap(stackRequests),
                demandSolution.scheduleStates(),
                demandSolution.mipNanos(),
                demandSolution.quality(),
                FastUtilCollections.immutableMap(retainedSeed),
                retainedSeedFinal,
                seedRefinementPasses));
    }

    private static Object2ObjectMap<AEKey, BigInteger> terminalSeedBalances(
                                                                            Object2ObjectMap<AEKey, BigInteger> initialInputs,
                                                                            ObjectList<TrinityPlanStage> stages,
                                                                            IntList stageOrder,
                                                                            ObjectList<TrinityCycleRepeatBlock> repeatBlocks,
                                                                            Object2ObjectMap<AEKey, BigInteger> retainedSeed) {
        if (retainedSeed.isEmpty()) {
            return FastUtilCollections.mapOf();
        }
        Int2ObjectOpenHashMap<TrinityPlanStage> stagesByIndex = new Int2ObjectOpenHashMap<>();
        stages.forEach(stage -> stagesByIndex.put(stage.index(), stage));
        Int2ObjectOpenHashMap<TrinityCycleRepeatBlock> blocksByStage = new Int2ObjectOpenHashMap<>();
        repeatBlocks.forEach(block -> block.stageOrder().forEach(
                stageIndex -> blocksByStage.put(stageIndex, block)));
        IntOpenHashSet completedBlocks = new IntOpenHashSet();
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> balances = new Object2ObjectLinkedOpenHashMap<>(initialInputs);
        for (int stageIndex : stageOrder) {
            TrinityPlanStage stage = stagesByIndex.get(stageIndex);
            if (!stage.cycleStage()) {
                mergeScaled(balances, stage.netChange(), BigInteger.ONE);
                continue;
            }
            TrinityCycleRepeatBlock block = blocksByStage.get(stageIndex);
            if (completedBlocks.add(block.index())) {
                mergeScaled(balances, block.netChange(), BigInteger.ONE);
            }
        }
        removeZeros(balances);
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> retainedBalances = new Object2ObjectLinkedOpenHashMap<>();
        retainedSeed.keySet().forEach(
                key -> retainedBalances.put(key, balances.getOrDefault(key, BigInteger.ZERO)));
        return FastUtilCollections.immutableMap(retainedBalances);
    }

    /**
     * Applies exact byte estimation, statistics, and the final immutable plan builder.
     */
    public TrinityCraftingPlan finalizePlan(
                                            TrinityGraphPlanContext context,
                                            TrinityGraphPlanAssembly assembly) {
        if (context == null || assembly == null) {
            throw new IllegalArgumentException("A Trinity final plan assembly request is incomplete");
        }
        var stackRequests = new Object2ObjectLinkedOpenHashMap<>(assembly.stackRequests());
        var tools = new Object2ObjectLinkedOpenHashMap<AEKey, BigInteger>();
        for (var stage : assembly.stages()) for (var firing : stage.firings()) for (var binding : firing.exactBindings()) {
            if (binding.lifetimeBudget()) {
                AEKey key = binding.template().what();
                tools.merge(key, stage.requiredAtStart().getOrDefault(key, binding.consumedAmount()), BigInteger::max);
            }
        }
        tools.forEach((key, amount) -> stackRequests.merge(key, amount.multiply(BigInteger.TWO), BigInteger::add));
        BigInteger bytes = this.byteEstimator.estimate(new TrinityPlanByteEstimateInput(
                stackRequests,
                sum(assembly.patternFirings()),
                BigInteger.valueOf(assembly.stages().size())));
        long elapsedNanos = Math.max(
                assembly.mipNanos(),
                Math.max(0L, System.nanoTime() - context.startedNanos()));
        TrinityPlanningStatistics statistics = new TrinityPlanningStatistics(
                context.topology().components().size(),
                context.variants().size(),
                elapsedNanos,
                elapsedNanos,
                assembly.mipNanos(),
                assembly.scheduleStates(),
                0,
                0,
                0,
                0,
                assembly.quality(),
                assembly.retainedSeed().size(),
                sum(assembly.retainedSeed()),
                sum(assembly.retainedSeedFinal()),
                assembly.seedRefinementPasses());
        return TrinityCraftingPlan.builder()
                .finalOutput(new GenericStack(context.target(), context.requestedAmount().longValueExact()))
                .bytes(bytes)
                .multiplePaths(hasMultiplePaths(context.variants()))
                .catalogRevision(context.catalogRevision())
                .quantityMode(context.quantityMode())
                .sameItemPolicy(context.sameItemPolicy())
                .initialExpectedInputs(assembly.initialInputs())
                .patternFirings(assembly.patternFirings())
                .stages(assembly.stages())
                .stageOrder(IntList.of(assembly.stageOrder().toIntArray()))
                .cycleRepeatBlocks(assembly.repeatBlocks())
                .minimumSeed(assembly.minimumSeed())
                .targetNetChange(assembly.netChange())
                .emittedItems(FastUtilCollections.mapOf())
                .diagnostics(ObjectList.of())
                .statistics(statistics)
                .build();
    }

    private static void appendOneTimeStages(
                                            ObjectList<TrinityVariantFiring> order,
                                            ObjectList<TrinityPlanStage> stages,
                                            IntList stageOrder,
                                            Object2ObjectMap<TrinityPatternIdentity, BigInteger> patternFirings,
                                            Object2ObjectMap<AEKey, BigInteger> stackRequests) {
        for (TrinityVariantFiring batch : order) {
            int stageIndex = stages.size();
            stages.add(stage(
                    stageIndex,
                    false,
                    batch.variant(),
                    batch.count()));
            stageOrder.add(stageIndex);
            mergePatternFiring(patternFirings, batch.variant(), batch.count());
            chargeStacks(stackRequests, batch.variant(), batch.count());
        }
    }

    private static Object2ObjectMap<AEKey, BigInteger> repeatedNetChange(
                                                                         ObjectList<TrinityVariantFiring> order,
                                                                         BigInteger repetitions) {
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> netChange = new Object2ObjectLinkedOpenHashMap<>();
        order.forEach(batch -> mergeScaled(
                netChange,
                batch.variant().netChange(),
                batch.count().multiply(repetitions)));
        removeZeros(netChange);
        return FastUtilCollections.immutableMap(netChange);
    }

    private static Object2ObjectMap<AEKey, BigInteger> minimumBalances(ObjectList<TrinityVariantFiring> order) {
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> required = new Object2ObjectLinkedOpenHashMap<>();
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> balances = new Object2ObjectLinkedOpenHashMap<>();
        for (TrinityVariantFiring firing : order) {
            requiredAtStart(firing.variant(), firing.count()).forEach((key, amount) -> {
                BigInteger deficit = amount.subtract(balances.getOrDefault(key, BigInteger.ZERO));
                if (deficit.signum() > 0) {
                    required.merge(key, deficit, BigInteger::add);
                    balances.merge(key, deficit, BigInteger::add);
                }
            });
            mergeScaled(balances, firing.variant().netChange(), firing.count());
        }
        balances.values().forEach(amount -> {
            if (amount.signum() < 0) {
                throw new IllegalStateException("A Trinity cycle unit requires an unaccounted entry balance");
            }
        });
        return FastUtilCollections.immutableMap(required);
    }

    private static TrinityPlanStage stage(
                                          int index,
                                          boolean cycle,
                                          TrinityPatternVariant variant,
                                          BigInteger count) {
        // Returned inputs are available to later firings in the same batch, including ordinary tool use.
        Object2ObjectMap<AEKey, BigInteger> required = requiredAtStart(variant, count);
        return new TrinityPlanStage(
                index,
                cycle,
                IntSets.emptySet(),
                ObjectList.of(new TrinityPlanPatternFiring(
                        variant.patternIdentity(),
                        variant.primaryOutput(),
                        variant.ordinal(),
                        count,
                        variant.physicalInputs(),
                        variant.declaredOutputs(),
                        variant.physicalRemainingOutputs(),
                        variant.requiresExactBinding() ? variant.bindings() : ObjectList.of())),
                required,
                multiplySigned(variant.netChange(), count));
    }

    private static Object2ObjectMap<AEKey, BigInteger> requiredAtStart(
                                                                       TrinityPatternVariant variant,
                                                                       BigInteger count) {
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> required = new Object2ObjectLinkedOpenHashMap<>();
        variant.inputs().forEach((key, input) -> {
            BigInteger net = variant.netChange().getOrDefault(key, BigInteger.ZERO);
            BigInteger amount = net.signum() < 0 ?
                    input.add(net.negate().multiply(count.subtract(BigInteger.ONE))) :
                    input;
            required.put(key, amount);
        });
        return FastUtilCollections.immutableMap(required);
    }

    private static Object2ObjectMap<AEKey, BigInteger> multiplySigned(
                                                                      Object2ObjectMap<AEKey, BigInteger> amounts,
                                                                      BigInteger multiplier) {
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> result = new Object2ObjectLinkedOpenHashMap<>();
        amounts.forEach((key, amount) -> {
            BigInteger multiplied = amount.multiply(multiplier);
            if (multiplied.signum() != 0) {
                result.put(key, multiplied);
            }
        });
        return FastUtilCollections.immutableMap(result);
    }

    private static Int2IntMap topologicalPositions(TrinityCraftingTopology topology) {
        Int2IntOpenHashMap positions = new Int2IntOpenHashMap();
        for (int position = 0; position < topology.topologicalOrder().size(); position++) {
            positions.put(topology.topologicalOrder().getInt(position), position);
        }
        return positions;
    }

    private static boolean hasMultiplePaths(ObjectList<TrinityPatternVariant> variants) {
        Object2IntMap<AEKey> producerCounts = new Object2IntOpenHashMap<>();
        for (TrinityPatternVariant variant : variants) {
            for (AEKey output : variant.outputs().keySet()) {
                int count = producerCounts.mergeInt(output, 1, Integer::sum);
                if (count > 1) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void mergePatternFiring(
                                           Object2ObjectMap<TrinityPatternIdentity, BigInteger> firings,
                                           TrinityPatternVariant variant,
                                           BigInteger count) {
        firings.merge(variant.patternIdentity(), count, BigInteger::add);
    }

    /** Retained tools occupy physical units once; their lifetime budget is not a per-operation stack transfer. */
    private static void chargeStacks(Object2ObjectMap<AEKey, BigInteger> requests, TrinityPatternVariant variant, BigInteger count) {
        for (var amounts : ObjectList.of(variant.inputs(), variant.outputs())) {
            amounts.forEach((key, amount) -> {
                BigInteger physical = amount.subtract(variant.lifetimeTools().getOrDefault(key, BigInteger.ZERO));
                if (physical.signum() > 0) requests.merge(key, physical.multiply(count), BigInteger::add);
            });
        }
    }

    private static void mergeScaled(
                                    Object2ObjectMap<AEKey, BigInteger> target,
                                    Object2ObjectMap<AEKey, BigInteger> source,
                                    BigInteger multiplier) {
        source.forEach((key, amount) -> target.merge(key, amount.multiply(multiplier), BigInteger::add));
    }

    private static void removeZeros(Object2ObjectMap<AEKey, BigInteger> amounts) {
        amounts.entrySet().removeIf(entry -> entry.getValue().signum() == 0);
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

    private sealed interface OrderedUnit permits AcyclicUnit, CycleUnit {

        int rank();

        String stableKey();
    }

    private record AcyclicUnit(
                               int rank,
                               TrinityPatternVariant variant,
                               BigInteger count)
            implements OrderedUnit {

        @Override
        public String stableKey() {
            return "0:" + this.variant.patternIdentity().publicationEncoding() + ':' + this.variant.ordinal();
        }
    }

    private record CycleUnit(
                             int rank,
                             int sequence,
                             TrinityCycleSelection solution)
            implements OrderedUnit {

        @Override
        public String stableKey() {
            return "1:" + String.format("%010d", this.sequence);
        }
    }
}
