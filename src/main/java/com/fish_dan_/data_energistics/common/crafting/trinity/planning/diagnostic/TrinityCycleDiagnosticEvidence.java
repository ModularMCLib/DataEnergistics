package com.fish_dan_.data_energistics.common.crafting.trinity.planning.diagnostic;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.TrinityCycleDemand;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.TrinityCyclePlan;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.joint.TrinityJointCyclePlan;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.selection.TrinityCycleSelection;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.schedule.TrinityVariantFiring;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityPlanQuality;
import com.fish_dan_.data_energistics.util.FastUtilCollections;

import appeng.api.stacks.AEKey;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectList;

import java.math.BigInteger;

/**
 * Complete non-executable proof that one cyclic component has an exact firing vector and prefix-safe compressed order.
 *
 * <p>
 * This type deliberately does not expose a conversion to {@link TrinityCycleSelection}; diagnostic inventory may
 * contain virtual missing inputs and must never enter graph assembly or an executable incumbent.
 * </p>
 */
public final class TrinityCycleDiagnosticEvidence {

    private final int componentIndex;
    private final TrinityCycleDemand demand;
    private final ObjectList<TrinityVariantFiring> prefixOrder;
    private final ObjectList<TrinityVariantFiring> localOrder;
    private final BigInteger repetitions;
    private final ObjectList<TrinityVariantFiring> suffixOrder;
    private final Object2ObjectMap<AEKey, BigInteger> minimumSeed;
    private final Object2ObjectMap<AEKey, BigInteger> initialInputs;
    private final Object2ObjectMap<AEKey, BigInteger> netChange;
    private final int scheduleStates;
    private final long mipNanos;
    private final TrinityPlanQuality quality;

    /**
     * Freezes all proof data and repeats exact aggregate-net and final-balance validation.
     */
    private TrinityCycleDiagnosticEvidence(
                                           int componentIndex,
                                           TrinityCycleDemand demand,
                                           ObjectList<TrinityVariantFiring> prefixOrder,
                                           ObjectList<TrinityVariantFiring> localOrder,
                                           BigInteger repetitions,
                                           ObjectList<TrinityVariantFiring> suffixOrder,
                                           Object2ObjectMap<AEKey, BigInteger> minimumSeed,
                                           Object2ObjectMap<AEKey, BigInteger> initialInputs,
                                           Object2ObjectMap<AEKey, BigInteger> netChange,
                                           int scheduleStates,
                                           long mipNanos,
                                           TrinityPlanQuality quality) {
        if (componentIndex < 0 || localOrder.isEmpty() || repetitions.signum() <= 0 || scheduleStates < 0 ||
                mipNanos < 0L) {
            throw new IllegalArgumentException("A Trinity diagnostic cycle requires a complete schedule proof");
        }
        prefixOrder = FastUtilCollections.immutableList(prefixOrder);
        localOrder = FastUtilCollections.immutableList(localOrder);
        suffixOrder = FastUtilCollections.immutableList(suffixOrder);
        minimumSeed = validatePositiveAmounts(minimumSeed, "minimum seed");
        initialInputs = validatePositiveAmounts(initialInputs, "initial input");
        netChange = validateSignedAmounts(netChange);

        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> calculatedNet = new Object2ObjectLinkedOpenHashMap<>();
        mergeNet(calculatedNet, prefixOrder, BigInteger.ONE);
        mergeNet(calculatedNet, localOrder, repetitions);
        mergeNet(calculatedNet, suffixOrder, BigInteger.ONE);
        calculatedNet.entrySet().removeIf(entry -> entry.getValue().signum() == 0);
        if (!calculatedNet.equals(netChange)) {
            throw new IllegalArgumentException("A Trinity diagnostic cycle order must match its exact net change");
        }
        for (Object2ObjectMap.Entry<AEKey, BigInteger> seed : minimumSeed.object2ObjectEntrySet()) {
            if (initialInputs.getOrDefault(seed.getKey(), BigInteger.ZERO).compareTo(seed.getValue()) < 0) {
                throw new IllegalArgumentException("A Trinity diagnostic cycle input must include its minimum seed");
            }
        }
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> finalBalances = new Object2ObjectLinkedOpenHashMap<>(
                initialInputs);
        netChange.forEach((key, amount) -> finalBalances.merge(key, amount, BigInteger::add));
        if (finalBalances.values().stream().anyMatch(amount -> amount.signum() < 0)) {
            throw new IllegalArgumentException("A Trinity diagnostic cycle final balance cannot be negative");
        }
        this.componentIndex = componentIndex;
        this.demand = demand;
        this.prefixOrder = prefixOrder;
        this.localOrder = localOrder;
        this.repetitions = repetitions;
        this.suffixOrder = suffixOrder;
        this.minimumSeed = minimumSeed;
        this.initialInputs = initialInputs;
        this.netChange = netChange;
        this.scheduleStates = scheduleStates;
        this.mipNanos = mipNanos;
        this.quality = quality;
    }

    /**
     * Creates evidence only from the scalar planner's validated repeat schedule.
     */
    public static TrinityCycleDiagnosticEvidence fromDeterministicPlan(
                                                                       int componentIndex,
                                                                       TrinityCycleDemand demand,
                                                                       TrinityCyclePlan plan) {
        return new TrinityCycleDiagnosticEvidence(
                componentIndex,
                demand,
                ObjectList.of(),
                plan.oneCycleOrder(),
                plan.repetitions(),
                ObjectList.of(),
                plan.minimumSeed(),
                plan.initialInputs(),
                plan.netChange(),
                plan.schedule().statesVisited(),
                0L,
                TrinityPlanQuality.VERIFIED_FEASIBLE);
    }

    /**
     * Creates evidence only from the joint evaluator's validated compressed schedule.
     */
    public static TrinityCycleDiagnosticEvidence fromJointPlan(
                                                               int componentIndex,
                                                               TrinityCycleDemand demand,
                                                               TrinityJointCyclePlan plan) {
        return new TrinityCycleDiagnosticEvidence(
                componentIndex,
                demand,
                ObjectList.of(),
                plan.schedule().batches(),
                BigInteger.ONE,
                ObjectList.of(),
                plan.minimumSeed(),
                plan.initialInputs(),
                plan.netChange(),
                plan.searchStates(),
                plan.solverNanos(),
                plan.quality());
    }

    /**
     * Copies an already validated executable selection into a type that cannot be submitted for execution.
     */
    public static TrinityCycleDiagnosticEvidence fromSelection(
                                                               TrinityCycleSelection selection,
                                                               TrinityCycleDemand demand) {
        return new TrinityCycleDiagnosticEvidence(
                selection.componentIndex(),
                demand,
                selection.prefixOrder(),
                selection.localOrder(),
                selection.repetitions(),
                selection.suffixOrder(),
                selection.minimumSeed(),
                selection.initialInputs(),
                selection.netChange(),
                selection.scheduleStates(),
                selection.mipNanos(),
                selection.quality());
    }

    public int componentIndex() {
        return this.componentIndex;
    }

    public TrinityCycleDemand demand() {
        return this.demand;
    }

    public ObjectList<TrinityVariantFiring> localOrder() {
        return this.localOrder;
    }

    /**
     * Returns immutable one-time prefix evidence; display-only callers must not multiply it by repetitions.
     */
    public ObjectList<TrinityVariantFiring> prefixOrder() {
        return this.prefixOrder;
    }

    /**
     * Returns immutable one-time suffix evidence, after all repeats; this does not expose an executable plan.
     */
    public ObjectList<TrinityVariantFiring> suffixOrder() {
        return this.suffixOrder;
    }

    public BigInteger repetitions() {
        return this.repetitions;
    }

    public Object2ObjectMap<AEKey, BigInteger> minimumSeed() {
        return this.minimumSeed;
    }

    public Object2ObjectMap<AEKey, BigInteger> initialInputs() {
        return this.initialInputs;
    }

    public Object2ObjectMap<AEKey, BigInteger> netChange() {
        return this.netChange;
    }

    public int scheduleStates() {
        return this.scheduleStates;
    }

    public long mipNanos() {
        return this.mipNanos;
    }

    public TrinityPlanQuality quality() {
        return this.quality;
    }

    /**
     * Reconstructs every declared output produced by the validated prefix/repeat/suffix schedule.
     */
    public Object2ObjectMap<AEKey, BigInteger> emittedItems() {
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> emitted = new Object2ObjectLinkedOpenHashMap<>();
        mergeOutputs(emitted, this.prefixOrder, BigInteger.ONE);
        mergeOutputs(emitted, this.localOrder, this.repetitions);
        mergeOutputs(emitted, this.suffixOrder, BigInteger.ONE);
        return FastUtilCollections.immutableMap(emitted);
    }

    private static void mergeNet(
                                 Object2ObjectMap<AEKey, BigInteger> target,
                                 ObjectList<TrinityVariantFiring> order,
                                 BigInteger multiplier) {
        order.forEach(firing -> firing.variant().netChange().forEach(
                (key, amount) -> target.merge(
                        key,
                        amount.multiply(firing.count()).multiply(multiplier),
                        BigInteger::add)));
    }

    private static void mergeOutputs(
                                     Object2ObjectMap<AEKey, BigInteger> target,
                                     ObjectList<TrinityVariantFiring> order,
                                     BigInteger multiplier) {
        order.forEach(firing -> firing.variant().outputs().forEach(
                (key, amount) -> target.merge(
                        key,
                        amount.multiply(firing.count()).multiply(multiplier),
                        BigInteger::add)));
    }

    private static Object2ObjectMap<AEKey, BigInteger> validatePositiveAmounts(
                                                                               Object2ObjectMap<AEKey, BigInteger> source,
                                                                               String role) {
        source.forEach((key, amount) -> {
            if (amount.signum() <= 0) {
                throw new IllegalArgumentException("A Trinity diagnostic cycle " + role + " must be positive");
            }
        });
        return FastUtilCollections.immutableMap(source);
    }

    private static Object2ObjectMap<AEKey, BigInteger> validateSignedAmounts(Object2ObjectMap<AEKey, BigInteger> source) {
        source.forEach((key, amount) -> {
            if (amount.signum() == 0) {
                throw new IllegalArgumentException("A Trinity diagnostic cycle net amount must be non-zero");
            }
        });
        return FastUtilCollections.immutableMap(source);
    }
}
