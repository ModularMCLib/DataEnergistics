package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.orchestration.assembly;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityCycleRepeatBlock;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityPlanStage;

import appeng.api.stacks.AEKey;

import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.ints.IntSets;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import org.jspecify.annotations.Nullable;

import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;

/**
 * Derives quantity-proven execution dependencies without turning shared keys or repeat blocks into global barriers.
 *
 * <p>
 * Each initial or produced balance is represented as a stable token lot. A stage depends only on the earlier units
 * whose returned or produced lots it actually needs. Unused initial lots remain available to later stages, so two
 * consumers may run together when the captured initial balance covers both. A scarce catalyst returned by one unit
 * becomes a lot owned by that unit, which preserves the required order for a later consumer without serializing
 * consumers backed by different lots.
 * </p>
 *
 * <p>
 * A compressed repeat block is one allocation unit: its minimum seed is the transient start requirement, its exact
 * negative net change is also reserved, its first stage receives external dependencies, and its last stage is the
 * completion anchor. The repeat cursor remains the sole authority for ordering stages inside the block.
 * </p>
 */
final class TrinityStageDependencyPlanner {

    private TrinityStageDependencyPlanner() {}

    /**
     * Replaces provisional empty dependencies with exact quantity-proven predecessor sets.
     *
     * @param initialInputs initial balances owned by the executable plan
     * @param stages        provisional stages with exact start requirements and net changes
     * @param stageOrder    stable sequential order already verified by the planner
     * @param repeatBlocks  compressed cycle units referenced by the stages
     * @return immutable stages carrying quantity-proven dependencies
     */
    static List<TrinityPlanStage> plan(
                                       Map<AEKey, BigInteger> initialInputs,
                                       List<TrinityPlanStage> stages,
                                       IntList stageOrder,
                                       List<TrinityCycleRepeatBlock> repeatBlocks) {
        Int2ObjectMap<TrinityPlanStage> stagesByIndex = stagesByIndex(stages);
        Int2ObjectMap<TrinityCycleRepeatBlock> repeatByStage = repeatByStage(repeatBlocks, stagesByIndex);
        List<ExecutionUnit> units = executionUnits(stageOrder, stagesByIndex, repeatByStage);
        Int2ObjectMap<IntSet> dependenciesByEntry = allocateDependencies(initialInputs, units);

        ObjectArrayList<TrinityPlanStage> planned = new ObjectArrayList<>(stages.size());
        for (TrinityPlanStage stage : stages) {
            planned.add(new TrinityPlanStage(
                    stage.index(),
                    stage.cycleStage(),
                    dependenciesByEntry.getOrDefault(stage.index(), IntSets.emptySet()),
                    stage.firings(),
                    stage.requiredAtStart(),
                    stage.netChange()));
        }
        return List.copyOf(planned);
    }

    private static Int2ObjectMap<TrinityPlanStage> stagesByIndex(List<TrinityPlanStage> stages) {
        Int2ObjectLinkedOpenHashMap<TrinityPlanStage> indexed = new Int2ObjectLinkedOpenHashMap<>();
        for (TrinityPlanStage stage : stages) {
            if (indexed.putIfAbsent(stage.index(), stage) != null) {
                throw new IllegalArgumentException("Trinity dependency planning requires unique non-null stages");
            }
        }
        return indexed;
    }

    private static Int2ObjectMap<TrinityCycleRepeatBlock> repeatByStage(
                                                                        List<TrinityCycleRepeatBlock> repeatBlocks,
                                                                        Int2ObjectMap<TrinityPlanStage> stages) {
        Int2ObjectLinkedOpenHashMap<TrinityCycleRepeatBlock> indexed = new Int2ObjectLinkedOpenHashMap<>();
        for (TrinityCycleRepeatBlock block : repeatBlocks) {
            for (int stageIndex : block.stageOrder()) {
                TrinityPlanStage stage = stages.get(stageIndex);
                if (!stage.cycleStage() || indexed.putIfAbsent(stageIndex, block) != null) {
                    throw new IllegalArgumentException("Trinity dependency planning found an invalid repeat stage");
                }
            }
        }
        for (TrinityPlanStage stage : stages.values()) {
            if (stage.cycleStage() != indexed.containsKey(stage.index())) {
                throw new IllegalArgumentException("Every Trinity cycle stage must belong to exactly one repeat block");
            }
        }
        return indexed;
    }

    private static List<ExecutionUnit> executionUnits(
                                                      IntList stageOrder,
                                                      Int2ObjectMap<TrinityPlanStage> stages,
                                                      Int2ObjectMap<TrinityCycleRepeatBlock> repeatByStage) {
        IntSet orderSet = new IntOpenHashSet(stageOrder);
        if (stageOrder.size() != stages.size() || !orderSet.equals(stages.keySet())) {
            throw new IllegalArgumentException("Trinity dependency planning requires one complete stage order");
        }
        ObjectArrayList<ExecutionUnit> units = new ObjectArrayList<>();
        int position = 0;
        while (position < stageOrder.size()) {
            int stageIndex = stageOrder.getInt(position);
            TrinityPlanStage stage = stages.get(stageIndex);
            if (!stage.cycleStage()) {
                units.add(ExecutionUnit.forStage(stage));
                position++;
                continue;
            }

            TrinityCycleRepeatBlock block = repeatByStage.get(stageIndex);
            if (block.stageOrder().getInt(0) != stageIndex) {
                throw new IllegalArgumentException("A Trinity repeat block must begin at its first ordered stage");
            }
            for (int blockStage : block.stageOrder()) {
                if (position >= stageOrder.size() || stageOrder.getInt(position) != blockStage) {
                    throw new IllegalArgumentException("A Trinity repeat block must be contiguous in execution order");
                }
                position++;
            }
            units.add(ExecutionUnit.forRepeat(block));
        }
        return List.copyOf(units);
    }

    private static Int2ObjectMap<IntSet> allocateDependencies(
                                                              Map<AEKey, BigInteger> initialInputs,
                                                              List<ExecutionUnit> units) {
        Object2ObjectLinkedOpenHashMap<AEKey, ArrayDeque<TokenLot>> balances = new Object2ObjectLinkedOpenHashMap<>();
        initialInputs.forEach((key, amount) -> {
            if (amount.signum() <= 0) {
                throw new IllegalArgumentException("Trinity dependency initial balances must be positive");
            }
            balances.computeIfAbsent(key, ignored -> new ArrayDeque<>())
                    .addLast(new TokenLot(amount, null));
        });

        Int2ObjectLinkedOpenHashMap<IntSet> dependencies = new Int2ObjectLinkedOpenHashMap<>();
        for (ExecutionUnit unit : units) {
            IntOpenHashSet unitDependencies = new IntOpenHashSet();
            Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> requirements = reservationRequirements(unit);
            ObjectLinkedOpenHashSet<AEKey> touchedKeys = new ObjectLinkedOpenHashSet<>(requirements.keySet());
            touchedKeys.addAll(unit.netChange().keySet());
            for (AEKey key : touchedKeys) {
                BigInteger required = requirements.getOrDefault(key, BigInteger.ZERO);
                consumeLots(balances, key, required, unitDependencies);
                BigInteger returned = required.add(unit.netChange().getOrDefault(key, BigInteger.ZERO));
                if (returned.signum() < 0) {
                    throw new IllegalStateException("A Trinity dependency unit returned a negative material balance");
                }
                if (returned.signum() > 0) {
                    balances.computeIfAbsent(key, ignored -> new ArrayDeque<>())
                            .addLast(new TokenLot(returned, unit.completionStage()));
                }
            }
            dependencies.put(unit.entryStage(), IntSets.unmodifiable(unitDependencies));
        }
        return dependencies;
    }

    private static Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> reservationRequirements(ExecutionUnit unit) {
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> requirements = new Object2ObjectLinkedOpenHashMap<>(unit.requiredAtStart());
        unit.netChange().forEach((key, change) -> {
            if (change.signum() < 0) {
                requirements.merge(key, change.negate(), BigInteger::max);
            }
        });
        return requirements;
    }

    private static void consumeLots(
                                    Map<AEKey, ArrayDeque<TokenLot>> balances,
                                    AEKey key,
                                    BigInteger required,
                                    IntSet dependencies) {
        BigInteger remaining = required;
        ArrayDeque<TokenLot> lots = balances.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        while (remaining.signum() > 0) {
            TokenLot lot = lots.pollFirst();
            if (lot == null) {
                throw new IllegalStateException("A validated Trinity execution order lacks a required material balance");
            }
            BigInteger consumed = remaining.min(lot.amount());
            if (lot.sourceStage() != null) {
                dependencies.add(lot.sourceStage().intValue());
            }
            BigInteger leftover = lot.amount().subtract(consumed);
            if (leftover.signum() > 0) {
                lots.addFirst(new TokenLot(leftover, lot.sourceStage()));
            }
            remaining = remaining.subtract(consumed);
        }
    }

    private record TokenLot(BigInteger amount, @Nullable Integer sourceStage) {

        private TokenLot {
            if (amount.signum() <= 0) {
                throw new IllegalArgumentException("A Trinity dependency token lot must be positive");
            }
        }
    }

    private record ExecutionUnit(
                                 int entryStage,
                                 int completionStage,
                                 Map<AEKey, BigInteger> requiredAtStart,
                                 Map<AEKey, BigInteger> netChange) {

        private ExecutionUnit {
            requiredAtStart = Map.copyOf(requiredAtStart);
            netChange = Map.copyOf(netChange);
        }

        private static ExecutionUnit forStage(TrinityPlanStage stage) {
            return new ExecutionUnit(
                    stage.index(),
                    stage.index(),
                    stage.requiredAtStart(),
                    stage.netChange());
        }

        private static ExecutionUnit forRepeat(TrinityCycleRepeatBlock block) {
            return new ExecutionUnit(
                    block.stageOrder().getInt(0),
                    block.stageOrder().getInt(block.stageOrder().size() - 1),
                    block.minimumSeed(),
                    block.netChange());
        }
    }
}
