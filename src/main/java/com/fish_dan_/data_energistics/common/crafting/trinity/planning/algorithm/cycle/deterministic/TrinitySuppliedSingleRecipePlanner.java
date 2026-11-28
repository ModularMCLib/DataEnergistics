package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.deterministic;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningControl;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.TrinityCycleDemand;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.deterministic.support.TrinityDeterministicDiagnostics;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.seed.TrinityCycleSeedRequirement;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.opportunity.TrinityPlanningAttempt;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.schedule.TrinityDeterministicRepeatScheduler;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.schedule.TrinityVariantFiring;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.topology.TrinityStronglyConnectedComponent;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;
import com.fish_dan_.data_energistics.utils.AmountMath;

import appeng.api.stacks.AEKey;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMaps;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.jspecify.annotations.Nullable;

import java.math.BigInteger;

/**
 * Exact affine execution for one recipe supplied by current stock or upstream production. In a multi-recipe cycle,
 * a direct conversion serves one net output from its sole producer and spends current internal stock.
 */
final class TrinitySuppliedSingleRecipePlanner {

    private static final TrinityDeterministicRepeatScheduler SCHEDULER = TrinityDeterministicRepeatScheduler.create();

    private TrinitySuppliedSingleRecipePlanner() {}

    static TrinityPlanningAttempt<TrinityDeterministicComponentPlan> plan(
                                                                          TrinityStronglyConnectedComponent component,
                                                                          TrinityCycleDemand demand,
                                                                          Object2ObjectMap<AEKey, BigInteger> available,
                                                                          ObjectSet<AEKey> producibleInputs,
                                                                          int maxStates,
                                                                          TrinityPlanningControl control) {
        TrinityPatternVariant variant = selectRecipe(component, demand, producibleInputs);
        if (variant == null) {
            return TrinityDeterministicDiagnostics.notApplicable();
        }
        ObjectSet<AEKey> upstreamSupply = producibleInputs;
        if (component.cycleVariants().size() > 1) {
            upstreamSupply = new ObjectOpenHashSet<>(producibleInputs);
            upstreamSupply.removeAll(component.keys());
        }
        BigInteger count = BigInteger.ONE;
        for (var bound : demand.requiredNetChangeLowerBounds().entrySet()) {
            BigInteger gain = variant.netChange().getOrDefault(bound.getKey(), BigInteger.ZERO);
            if (gain.signum() <= 0) {
                return TrinityDeterministicDiagnostics.notApplicable();
            }
            count = count.max(AmountMath.ceilDivideNonNegative(bound.getValue(), gain));
        }
        for (var bound : demand.finalBalanceLowerBounds().entrySet()) {
            AEKey key = bound.getKey();
            if (upstreamSupply.contains(key)) {
                continue;
            }
            BigInteger missing = bound.getValue().subtract(available.getOrDefault(key, BigInteger.ZERO));
            if (missing.signum() > 0) {
                BigInteger gain = variant.netChange().getOrDefault(key, BigInteger.ZERO);
                if (gain.signum() <= 0) {
                    return TrinityDeterministicDiagnostics.notApplicable();
                }
                count = count.max(AmountMath.ceilDivideNonNegative(missing, gain));
            }
        }

        BigInteger repetitions = count;
        ObjectList<TrinityVariantFiring> unit = ObjectList.of(new TrinityVariantFiring(variant, BigInteger.ONE));
        Object2ObjectMap<AEKey, BigInteger> minimumInputs = TrinityCycleSeedRequirement.repeatedMinimumInputs(unit, repetitions);
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> net = new Object2ObjectLinkedOpenHashMap<>();
        variant.netChange().forEach((key, amount) -> net.put(key, amount.multiply(repetitions)));
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> initial = new Object2ObjectLinkedOpenHashMap<>(minimumInputs);
        demand.finalBalanceLowerBounds().forEach((key, amount) -> {
            BigInteger required = amount.subtract(net.getOrDefault(key, BigInteger.ZERO));
            if (required.signum() > 0) {
                initial.merge(key, required, BigInteger::max);
            }
        });
        for (var input : initial.object2ObjectEntrySet()) {
            if (!upstreamSupply.contains(input.getKey()) &&
                    input.getValue().compareTo(available.getOrDefault(input.getKey(), BigInteger.ZERO)) > 0) {
                return TrinityDeterministicDiagnostics.notApplicable();
            }
        }
        var scheduled = SCHEDULER.schedule(unit, repetitions, initial, maxStates, control);
        if (!scheduled.successful()) {
            return TrinityPlanningAttempt.terminal(scheduled.diagnostic());
        }
        // This proves feasibility, not a global external-input optimum among larger co-product firing counts.
        return TrinityPlanningAttempt.feasible(new TrinityDeterministicComponentPlan(
                Object2ObjectMaps.singleton(variant, repetitions), minimumInputs, initial, net, scheduled.value()));
    }

    private static @Nullable TrinityPatternVariant selectRecipe(
                                                                TrinityStronglyConnectedComponent component,
                                                                TrinityCycleDemand demand,
                                                                ObjectSet<AEKey> producibleInputs) {
        var variants = component.cycleVariants();
        if (variants.size() == 1) {
            return producibleInputs.containsAll(component.keys()) ? variants.getFirst() : null;
        }
        if (demand.requiredNetChangeLowerBounds().size() != 1) {
            return null;
        }
        AEKey output = demand.requiredNetChangeLowerBounds().keySet().iterator().next();
        var producers = variants.stream()
                .filter(variant -> variant.netChange().getOrDefault(output, BigInteger.ZERO).signum() > 0)
                .limit(2)
                .toList();
        if (producers.size() != 1) {
            return null;
        }
        TrinityPatternVariant variant = producers.getFirst();
        return component.keys().stream().anyMatch(key -> variant.netChange().getOrDefault(key, BigInteger.ZERO).signum() < 0) ? variant : null;
    }
}
