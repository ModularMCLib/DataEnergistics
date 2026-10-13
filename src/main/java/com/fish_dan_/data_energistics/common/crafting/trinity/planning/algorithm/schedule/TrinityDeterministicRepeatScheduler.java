package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.schedule;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningControl;

import appeng.api.stacks.AEKey;

import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectList;

import java.math.BigInteger;

/**
 * Builds an exact logarithmic schedule for repeated executions of one known productive cycle.
 */
public interface TrinityDeterministicRepeatScheduler {

    /**
     * @return stateless exact repeat scheduler
     */
    static TrinityDeterministicRepeatScheduler create() {
        return new AffineTrinityDeterministicRepeatScheduler();
    }

    /**
     * @param oneCycleOrder   exact firing blocks for one cycle
     * @param repetitions     positive number of complete cycles
     * @param initialBalances exact seed and externally consumed inputs for all repetitions
     * @param maxStates       positive compressed-state limit
     * @param control         cancellation and deadline boundary
     * @return aggregate executable schedule without per-firing expansion
     */
    TrinityAlgorithmResult<TrinityCompressedSchedule> schedule(
                                                               ObjectList<TrinityVariantFiring> oneCycleOrder,
                                                               BigInteger repetitions,
                                                               Object2ObjectMap<AEKey, BigInteger> initialBalances,
                                                               int maxStates,
                                                               TrinityPlanningControl control);
}
