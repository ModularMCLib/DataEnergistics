package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.orchestration.demand;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.selection.TrinityCycleSelection;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityPlanQuality;
import com.fish_dan_.data_energistics.util.FastUtilCollections;

import appeng.api.stacks.AEKey;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectList;

import java.math.BigInteger;

/**
 * Immutable output of reverse graph-demand aggregation before execution stages are assembled.
 *
 * @param initialInputs  inventory reserved by the aggregate demand search
 * @param acyclicFirings aggregated non-cycle firings and their execution ranks
 * @param cycleSolutions selected executable cycle plans
 * @param scheduleStates combined cycle and route-search states
 * @param mipNanos       MIP time contributed by selected cycle plans
 */
public record TrinityGraphDemandSolution(
                                         Object2ObjectMap<AEKey, BigInteger> initialInputs,
                                         Object2ObjectMap<TrinityPatternVariant, TrinityRankedPatternFiring> acyclicFirings,
                                         ObjectList<TrinityCycleSelection> cycleSolutions,
                                         int scheduleStates,
                                         long mipNanos) {

    public TrinityGraphDemandSolution {
        initialInputs = FastUtilCollections.immutableMap(new Object2ObjectLinkedOpenHashMap<>(initialInputs));
        acyclicFirings = FastUtilCollections.immutableMap(new Object2ObjectLinkedOpenHashMap<>(acyclicFirings));
        cycleSolutions = FastUtilCollections.immutableList(cycleSolutions);
    }

    /**
     * @return weakest proof quality among every selected cycle
     */
    public TrinityPlanQuality quality() {
        TrinityPlanQuality quality = TrinityPlanQuality.PROVED_OPTIMAL;
        for (TrinityCycleSelection cycle : this.cycleSolutions) {
            quality = quality.combine(cycle.quality());
        }
        return quality;
    }
}
