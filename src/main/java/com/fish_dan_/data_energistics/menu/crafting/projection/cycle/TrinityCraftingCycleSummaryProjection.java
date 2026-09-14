package com.fish_dan_.data_energistics.menu.crafting.projection.cycle;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnostic;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.schedule.TrinityVariantFiring;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.diagnostic.TrinityCycleDiagnosticEvidence;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.gateway.TrinityDiagnosedCraftingPlan;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternIdentity;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityCraftingPlan;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityCycleRepeatBlock;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityPlanPatternFiring;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityPlanStage;
import com.fish_dan_.data_energistics.menu.crafting.projection.cycle.diagnostic.TrinityCraftingExactShortage;
import com.fish_dan_.data_energistics.menu.crafting.projection.cycle.diagnostic.TrinityCraftingUnresolvedDemand;
import com.fish_dan_.data_energistics.menu.crafting.projection.cycle.model.TrinityCraftingCycleHeader;
import com.fish_dan_.data_energistics.menu.crafting.projection.cycle.model.TrinityCraftingCycleMaterialContribution;
import com.fish_dan_.data_energistics.menu.crafting.projection.cycle.model.TrinityCraftingCycleSummary;
import com.fish_dan_.data_energistics.menu.crafting.projection.cycle.model.TrinityCraftingExactPlanAmounts;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;

import java.math.BigInteger;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Projects executable Trinity repeat blocks into exact confirmation-screen cycle statistics.
 */
public final class TrinityCraftingCycleSummaryProjection {

    private TrinityCraftingCycleSummaryProjection() {}

    /**
     * Creates one immutable summary without converting exact plan amounts through primitive numeric types.
     *
     * @param plan               validated executable Trinity plan
     * @param availableInventory current ME inventory snapshot used as the percentage denominator
     * @return cycle headers, material contributions and plan-wide inventory usage percentages
     */
    public static TrinityCraftingCycleSummary create(TrinityCraftingPlan plan, KeyCounter availableInventory) {
        Int2ObjectMap<TrinityPlanStage> stagesByIndex = indexStages(plan.stages());
        ObjectArrayList<TrinityCycleRepeatBlock> blocks = new ObjectArrayList<>(plan.cycleRepeatBlocks());
        blocks.sort(Comparator.comparingInt(TrinityCycleRepeatBlock::index));

        ObjectArrayList<TrinityCraftingCycleHeader> cycles = new ObjectArrayList<>(blocks.size());
        ObjectArrayList<TrinityCraftingCycleMaterialContribution> contributions = new ObjectArrayList<>();
        for (int index = 0; index < blocks.size(); index++) {
            TrinityCycleRepeatBlock block = blocks.get(index);
            int displayOrdinal = index + 1;
            BlockProjection projection = projectBlock(block, displayOrdinal, stagesByIndex);
            cycles.add(projection.cycle());
            contributions.addAll(projection.contributions());
        }
        return TrinityCraftingCycleSummary.create(
                projectInventoryUsage(plan.initialExpectedInputs(), availableInventory),
                cycles,
                contributions,
                List.of(),
                List.of(),
                exactPlanAmounts(plan.initialExpectedInputs(), Map.of(), plan.plannedOutputs()),
                Optional.of(plan.exactBytes()));
    }

    /**
     * Projects non-executable material diagnostics and only the cycles carrying a complete compressed-schedule proof.
     */
    public static TrinityCraftingCycleSummary create(
                                                     TrinityDiagnosedCraftingPlan plan,
                                                     KeyCounter availableInventory) {
        if (plan.ae2FallbackEstimate()) {
            throw new IllegalArgumentException("An AE2 fallback diagnostic cannot provide Trinity cycle evidence");
        }
        TrinityPlanningDiagnostic diagnostic = plan.diagnostic();
        ObjectArrayList<TrinityCycleDiagnosticEvidence> evidence = new ObjectArrayList<>(diagnostic.cycleEvidence());
        evidence.sort(Comparator.comparingInt(TrinityCycleDiagnosticEvidence::componentIndex));
        ObjectArrayList<TrinityCraftingCycleHeader> cycles = new ObjectArrayList<>(evidence.size());
        ObjectArrayList<TrinityCraftingCycleMaterialContribution> contributions = new ObjectArrayList<>();
        for (int index = 0; index < evidence.size(); index++) {
            BlockProjection projection = projectDiagnosticBlock(evidence.get(index), index + 1);
            cycles.add(projection.cycle());
            contributions.addAll(projection.contributions());
        }

        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> used = new Object2ObjectLinkedOpenHashMap<>();
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> emitted = new Object2ObjectLinkedOpenHashMap<>();
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> missing = new Object2ObjectLinkedOpenHashMap<>();
        ObjectArrayList<TrinityCraftingExactShortage> exactShortages = new ObjectArrayList<>();
        ObjectArrayList<TrinityCraftingUnresolvedDemand> unresolvedDemands = new ObjectArrayList<>();
        if (diagnostic.inputShortage().isPresent()) {
            TrinityPlanningDiagnostic.InputShortage shortage = diagnostic.inputShortage().orElseThrow();
            if (shortage.available().signum() > 0) {
                used.put(shortage.key(), shortage.available());
            }
            exactShortages.add(new TrinityCraftingExactShortage(
                    shortage.key(),
                    shortage.required(),
                    shortage.available(),
                    shortage.missing()));
            missing.put(shortage.key(), shortage.missing());
        } else if (diagnostic.partialPlan().isPresent()) {
            TrinityPlanningDiagnostic.PartialPlan partial = diagnostic.partialPlan().orElseThrow();
            used.putAll(partial.usedItems());
            emitted.putAll(partial.emittedItems());
            partial.inputRequirements().forEach((key, requirement) -> exactShortages.add(
                    new TrinityCraftingExactShortage(
                            key,
                            requirement.required(),
                            requirement.available(),
                            requirement.missing())));
            partial.inputRequirements().forEach((key, requirement) -> missing.put(key, requirement.missing()));
            partial.missingItems().forEach((key, amount) -> {
                if (!partial.inputRequirements().containsKey(key)) {
                    unresolvedDemands.add(new TrinityCraftingUnresolvedDemand(key, amount));
                }
            });
        } else {
            unresolvedDemands.add(new TrinityCraftingUnresolvedDemand(
                    plan.finalOutput().what(),
                    BigInteger.valueOf(plan.finalOutput().amount())));
        }
        return TrinityCraftingCycleSummary.create(
                projectInventoryUsage(used, availableInventory),
                cycles,
                contributions,
                exactShortages,
                unresolvedDemands,
                exactPlanAmounts(used, missing, emitted),
                Optional.empty());
    }

    private static List<TrinityCraftingExactPlanAmounts> exactPlanAmounts(
                                                                          Map<AEKey, BigInteger> stored,
                                                                          Map<AEKey, BigInteger> missing,
                                                                          Map<AEKey, BigInteger> crafting) {
        ObjectLinkedOpenHashSet<AEKey> keys = new ObjectLinkedOpenHashSet<>(stored.keySet());
        keys.addAll(missing.keySet());
        keys.addAll(crafting.keySet());
        ObjectArrayList<TrinityCraftingExactPlanAmounts> rows = new ObjectArrayList<>(keys.size());
        keys.forEach(key -> rows.add(new TrinityCraftingExactPlanAmounts(
                key,
                missing.getOrDefault(key, BigInteger.ZERO),
                stored.getOrDefault(key, BigInteger.ZERO),
                crafting.getOrDefault(key, BigInteger.ZERO))));
        return rows;
    }

    private static Object2IntMap<AEKey> projectInventoryUsage(Map<AEKey, BigInteger> inputs,
                                                              KeyCounter availableInventory) {
        Object2IntLinkedOpenHashMap<AEKey> usage = new Object2IntLinkedOpenHashMap<>();
        inputs.forEach((key, consumed) -> usage.put(
                key,
                inventoryUsageBasisPoints(consumed, availableInventory.get(key))));
        return usage;
    }

    private static int inventoryUsageBasisPoints(BigInteger consumed, long availableAmount) {
        if (availableAmount <= 0L) {
            return TrinityCraftingCycleSummary.MAX_INVENTORY_USAGE_BASIS_POINTS;
        }
        BigInteger available = BigInteger.valueOf(availableAmount);
        if (consumed.compareTo(available) >= 0) {
            return TrinityCraftingCycleSummary.MAX_INVENTORY_USAGE_BASIS_POINTS;
        }
        return consumed
                .multiply(BigInteger.valueOf(TrinityCraftingCycleSummary.MAX_INVENTORY_USAGE_BASIS_POINTS))
                .divide(available)
                .intValueExact();
    }

    private static Int2ObjectMap<TrinityPlanStage> indexStages(List<TrinityPlanStage> stages) {
        Int2ObjectOpenHashMap<TrinityPlanStage> stagesByIndex = new Int2ObjectOpenHashMap<>();
        stages.forEach(stage -> stagesByIndex.put(stage.index(), stage));
        return stagesByIndex;
    }

    private static BlockProjection projectBlock(TrinityCycleRepeatBlock block,
                                                int displayOrdinal,
                                                Int2ObjectMap<TrinityPlanStage> stagesByIndex) {
        BigInteger patternExecutions = BigInteger.ZERO;
        Set<TrinityPatternIdentity> patternTypes = new ObjectOpenHashSet<>();
        Object2ObjectLinkedOpenHashMap<AEKey, MaterialRoles> materials = new Object2ObjectLinkedOpenHashMap<>();
        for (int stageIndex : block.stageOrder()) {
            TrinityPlanStage stage = stagesByIndex.get(stageIndex);
            for (TrinityPlanPatternFiring firing : stage.firings()) {
                patternExecutions = patternExecutions.add(firing.count().multiply(block.repetitions()));
                patternTypes.add(firing.patternIdentity());
                firing.inputs().keySet().forEach(key -> materials
                        .computeIfAbsent(key, ignored -> new MaterialRoles())
                        .markInput());
                firing.outputs().keySet().forEach(key -> materials
                        .computeIfAbsent(key, ignored -> new MaterialRoles())
                        .markOutput());
            }
        }
        block.minimumSeed().forEach((key, amount) -> materials
                .computeIfAbsent(key, ignored -> new MaterialRoles())
                .setMinimumSeed(amount));
        block.netChange().forEach((key, amount) -> materials
                .computeIfAbsent(key, ignored -> new MaterialRoles())
                .setNetChange(amount));

        TrinityCraftingCycleHeader cycle = new TrinityCraftingCycleHeader(
                block.index(),
                displayOrdinal,
                block.repetitions(),
                patternExecutions,
                block.stageOrder().size(),
                patternTypes.size());
        ObjectArrayList<TrinityCraftingCycleMaterialContribution> contributions = new ObjectArrayList<>(materials.size());
        materials.forEach((key, roles) -> contributions.add(roles.toContribution(
                block.index(),
                displayOrdinal,
                key)));
        return new BlockProjection(cycle, List.copyOf(contributions));
    }

    private static BlockProjection projectDiagnosticBlock(
                                                          TrinityCycleDiagnosticEvidence evidence,
                                                          int displayOrdinal) {
        BigInteger patternExecutions = BigInteger.ZERO;
        Set<TrinityPatternIdentity> patternTypes = new ObjectOpenHashSet<>();
        Object2ObjectLinkedOpenHashMap<AEKey, MaterialRoles> materials = new Object2ObjectLinkedOpenHashMap<>();
        for (TrinityVariantFiring firing : evidence.localOrder()) {
            patternExecutions = patternExecutions.add(firing.count().multiply(evidence.repetitions()));
            TrinityPatternVariant variant = firing.variant();
            patternTypes.add(variant.patternIdentity());
            variant.inputs().keySet().forEach(key -> materials
                    .computeIfAbsent(key, ignored -> new MaterialRoles())
                    .markInput());
            variant.declaredOutputs().keySet().forEach(key -> materials
                    .computeIfAbsent(key, ignored -> new MaterialRoles())
                    .markOutput());
        }
        minimumBalances(evidence.localOrder()).forEach((key, amount) -> materials
                .computeIfAbsent(key, ignored -> new MaterialRoles())
                .setMinimumSeed(amount));
        repeatedNetChange(evidence.localOrder(), evidence.repetitions()).forEach((key, amount) -> materials
                .computeIfAbsent(key, ignored -> new MaterialRoles())
                .setNetChange(amount));

        TrinityCraftingCycleHeader cycle = new TrinityCraftingCycleHeader(
                evidence.componentIndex(),
                displayOrdinal,
                evidence.repetitions(),
                patternExecutions,
                evidence.localOrder().size(),
                patternTypes.size());
        ObjectArrayList<TrinityCraftingCycleMaterialContribution> contributions = new ObjectArrayList<>(materials.size());
        materials.forEach((key, roles) -> contributions.add(roles.toContribution(
                evidence.componentIndex(),
                displayOrdinal,
                key)));
        return new BlockProjection(cycle, List.copyOf(contributions));
    }

    private static Map<AEKey, BigInteger> minimumBalances(List<TrinityVariantFiring> order) {
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
        if (balances.values().stream().anyMatch(amount -> amount.signum() < 0)) {
            throw new IllegalStateException("A diagnostic Trinity cycle unit has an unaccounted entry balance");
        }
        return Collections.unmodifiableMap(required);
    }

    private static Map<AEKey, BigInteger> repeatedNetChange(
                                                            List<TrinityVariantFiring> order,
                                                            BigInteger repetitions) {
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> netChange = new Object2ObjectLinkedOpenHashMap<>();
        order.forEach(firing -> mergeScaled(
                netChange,
                firing.variant().netChange(),
                firing.count().multiply(repetitions)));
        netChange.entrySet().removeIf(entry -> entry.getValue().signum() == 0);
        return Collections.unmodifiableMap(netChange);
    }

    private static Map<AEKey, BigInteger> requiredAtStart(
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
        return Collections.unmodifiableMap(required);
    }

    private static void mergeScaled(
                                    Map<AEKey, BigInteger> target,
                                    Map<AEKey, BigInteger> amounts,
                                    BigInteger multiplier) {
        amounts.forEach((key, amount) -> target.merge(key, amount.multiply(multiplier), BigInteger::add));
    }

    private record BlockProjection(TrinityCraftingCycleHeader cycle,
                                   List<TrinityCraftingCycleMaterialContribution> contributions) {}

    private static final class MaterialRoles {

        private boolean input;
        private boolean output;
        private BigInteger minimumSeed = BigInteger.ZERO;
        private BigInteger netChange = BigInteger.ZERO;

        private void markInput() {
            this.input = true;
        }

        private void markOutput() {
            this.output = true;
        }

        private void setMinimumSeed(BigInteger amount) {
            this.minimumSeed = amount;
        }

        private void setNetChange(BigInteger amount) {
            this.netChange = amount;
        }

        private TrinityCraftingCycleMaterialContribution toContribution(int blockIndex,
                                                                        int displayOrdinal,
                                                                        AEKey key) {
            return new TrinityCraftingCycleMaterialContribution(
                    blockIndex,
                    displayOrdinal,
                    key,
                    this.input,
                    this.output,
                    this.minimumSeed,
                    this.netChange);
        }
    }
}
