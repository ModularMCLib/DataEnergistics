package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.schedule;

import com.fish_dan_.data_energistics.util.FastUtilCollections;

import appeng.api.stacks.AEKey;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;

import java.math.BigInteger;

/**
 * Minimum internal seed found for one fixed firing vector within the bounded compressed state space.
 *
 * @param externalInputs exact boundary balances required before execution
 * @param minimumSeed    exact internal balances required before execution
 * @param schedule       executable proof starting with both input categories
 */
public record TrinityMinimumSeedSchedule(
                                         Object2ObjectMap<AEKey, BigInteger> externalInputs,
                                         Object2ObjectMap<AEKey, BigInteger> minimumSeed,
                                         TrinityCompressedSchedule schedule) {

    /**
     * Copies the positive seed and retains one complete executable schedule.
     */
    public TrinityMinimumSeedSchedule {
        if (externalInputs == null || minimumSeed == null || schedule == null) {
            throw new IllegalArgumentException("A Trinity minimum-seed result requires seed and schedule");
        }
        externalInputs = copyPositive(externalInputs, "external input");
        minimumSeed = copyPositive(minimumSeed, "minimum seed");
    }

    private static Object2ObjectMap<AEKey, BigInteger> copyPositive(Object2ObjectMap<AEKey, BigInteger> source, String role) {
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> copied = new Object2ObjectLinkedOpenHashMap<>();
        source.forEach((key, amount) -> {
            if (key == null || amount == null || amount.signum() <= 0) {
                throw new IllegalArgumentException("A Trinity " + role + " must contain positive amounts");
            }
            copied.put(key, amount);
        });
        return FastUtilCollections.immutableMap(copied);
    }
}
