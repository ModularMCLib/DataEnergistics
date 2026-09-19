package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.selection;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.schedule.TrinityVariantFiring;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityPlanQuality;

import appeng.api.stacks.AEKey;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

/**
 * Immutable cycle algorithm selection consumed by graph-stage assembly.
 *
 * @param componentIndex       owning SCC index
 * @param prefixOrder          one-time residual batches executed before the complete cycle units
 * @param localOrder           compressed executable batches for one repeat unit
 * @param repetitions          positive repeat count applied by graph assembly
 * @param suffixOrder          one-time residual batches executed after every complete cycle unit
 * @param minimumSeed          exact prefix reserve exposed to graph assembly
 * @param initialInputs        exact initial inventory reserved for the selected cycle
 * @param netChange            exact signed aggregate cycle effect
 * @param exportableNet        settled positive outputs proved safe to expose outside the complete cycle block
 * @param scheduleStates       bounded search states visited
 * @param mipNanos             deterministic opportunity time plus ojAlgo solver time
 * @param quality              exact proof strength retained by this cycle
 * @param retainedSeed         internal balance that must remain after downstream settlement
 * @param seedRefinementPasses additional solves required to make the selected route restart-safe
 */
public record TrinityCycleSelection(
                                    int componentIndex,
                                    List<TrinityVariantFiring> prefixOrder,
                                    List<TrinityVariantFiring> localOrder,
                                    BigInteger repetitions,
                                    List<TrinityVariantFiring> suffixOrder,
                                    Map<AEKey, BigInteger> minimumSeed,
                                    Map<AEKey, BigInteger> initialInputs,
                                    Map<AEKey, BigInteger> netChange,
                                    Map<AEKey, BigInteger> exportableNet,
                                    int scheduleStates,
                                    long mipNanos,
                                    TrinityPlanQuality quality,
                                    Map<AEKey, BigInteger> retainedSeed,
                                    int seedRefinementPasses) {

    /**
     * Returns whether one local unit preserves every SCC balance and increases at least one of them.
     * Structural feedback from returned tools alone does not qualify as amplification.
     *
     * @param internalKeys keys owned by this selection's SCC
     * @return whether the local unit may be represented by a productive repeat block
     */
    public boolean hasProductiveRepeat(List<AEKey> internalKeys) {
        var localNet = new Object2ObjectLinkedOpenHashMap<AEKey, BigInteger>();
        for (TrinityVariantFiring batch : this.localOrder) {
            batch.variant().netChange().forEach((key, amount) -> localNet.merge(key, amount.multiply(batch.count()), BigInteger::add));
        }
        return internalKeys.stream().allMatch(key -> localNet.getOrDefault(key, BigInteger.ZERO).signum() >= 0) &&
                internalKeys.stream().anyMatch(key -> localNet.getOrDefault(key, BigInteger.ZERO).signum() > 0);
    }
}
