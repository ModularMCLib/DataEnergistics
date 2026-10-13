package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.joint;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.schedule.TrinityCompressedSchedule;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityPlanQuality;
import com.fish_dan_.data_energistics.util.FastUtilCollections;

import appeng.api.stacks.AEKey;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;

import java.math.BigInteger;

/**
 * Exact multi-route SCC result after joint objective selection and compressed reachability validation.
 *
 * @param firings        exact integer firing vector
 * @param externalInputs minimal boundary inputs under the first lexicographic objective
 * @param minimumSeed    exact prefix seed proven by scheduling
 * @param initialInputs  external inputs plus seed/final-total contribution reserved before execution
 * @param netChange      exact signed effect of all firings
 * @param schedule       compressed executable proof
 * @param searchStates   total firing-box and compressed-schedule states used to prove the result
 * @param solverPasses   completed ojAlgo optimisation passes
 * @param solverNanos    measured time spent in ojAlgo
 * @param quality        exact proof strength retained by this executable plan
 */
public record TrinityJointCyclePlan(
                                    Object2ObjectMap<TrinityPatternVariant, BigInteger> firings,
                                    Object2ObjectMap<AEKey, BigInteger> externalInputs,
                                    Object2ObjectMap<AEKey, BigInteger> minimumSeed,
                                    Object2ObjectMap<AEKey, BigInteger> initialInputs,
                                    Object2ObjectMap<AEKey, BigInteger> netChange,
                                    TrinityCompressedSchedule schedule,
                                    int searchStates,
                                    int solverPasses,
                                    long solverNanos,
                                    TrinityPlanQuality quality) {

    /**
     * Copies the result and repeats all final BigInteger conservation checks.
     */
    public TrinityJointCyclePlan {
        if (firings.isEmpty() || searchStates <= 0 || solverPasses <= 0 || solverNanos < 0L) {
            throw new IllegalArgumentException("A Trinity joint cycle plan requires complete exact accounting");
        }
        firings = copyPositiveFirings(firings);
        externalInputs = copyPositiveAmounts(externalInputs);
        minimumSeed = copyPositiveAmounts(minimumSeed);
        initialInputs = copyPositiveAmounts(initialInputs);
        netChange = copySignedNonZero(netChange);
        if (!schedule.aggregateFirings().equals(firings)) {
            throw new IllegalArgumentException("A Trinity joint cycle schedule must match its firing vector");
        }
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> calculatedNet = new Object2ObjectLinkedOpenHashMap<>();
        firings.forEach((variant, count) -> variant.netChange().forEach(
                (key, amount) -> calculatedNet.merge(key, amount.multiply(count), BigInteger::add)));
        calculatedNet.entrySet().removeIf(entry -> entry.getValue().signum() == 0);
        if (!calculatedNet.equals(netChange)) {
            throw new IllegalArgumentException("A Trinity joint cycle net change must equal its firing vector");
        }
        for (Object2ObjectMap.Entry<AEKey, BigInteger> external : externalInputs.object2ObjectEntrySet()) {
            requireIncluded(initialInputs, external.getKey(), external.getValue(), "external input");
        }
        for (Object2ObjectMap.Entry<AEKey, BigInteger> seed : minimumSeed.object2ObjectEntrySet()) {
            requireIncluded(initialInputs, seed.getKey(), seed.getValue(), "minimum seed");
        }
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> finalBalances = new Object2ObjectLinkedOpenHashMap<>(initialInputs);
        netChange.forEach((key, amount) -> finalBalances.merge(key, amount, BigInteger::add));
        if (finalBalances.values().stream().anyMatch(amount -> amount.signum() < 0)) {
            throw new IllegalArgumentException("A Trinity joint cycle final balance cannot be negative");
        }
        finalBalances.entrySet().removeIf(entry -> entry.getValue().signum() == 0);
        if (!finalBalances.equals(schedule.finalBalances())) {
            throw new IllegalArgumentException("A Trinity joint cycle schedule must conserve its final balances");
        }
    }

    private static void requireIncluded(Object2ObjectMap<AEKey, BigInteger> initialInputs,
                                        AEKey key,
                                        BigInteger amount,
                                        String role) {
        if (initialInputs.getOrDefault(key, BigInteger.ZERO).compareTo(amount) < 0) {
            throw new IllegalArgumentException("A Trinity joint cycle initial input must include every " + role);
        }
    }

    private static Object2ObjectMap<TrinityPatternVariant, BigInteger> copyPositiveFirings(
                                                                                           Object2ObjectMap<TrinityPatternVariant, BigInteger> source) {
        Object2ObjectLinkedOpenHashMap<TrinityPatternVariant, BigInteger> copied = new Object2ObjectLinkedOpenHashMap<>();
        source.forEach((variant, amount) -> {
            if (amount.signum() <= 0) {
                throw new IllegalArgumentException("A Trinity joint cycle firing count must be positive");
            }
            copied.put(variant, amount);
        });
        return FastUtilCollections.immutableMap(copied);
    }

    private static Object2ObjectMap<AEKey, BigInteger> copyPositiveAmounts(Object2ObjectMap<AEKey, BigInteger> source) {
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> copied = new Object2ObjectLinkedOpenHashMap<>();
        source.forEach((key, amount) -> {
            if (amount.signum() <= 0) {
                throw new IllegalArgumentException("A Trinity joint cycle amount must be positive");
            }
            copied.put(key, amount);
        });
        return FastUtilCollections.immutableMap(copied);
    }

    private static Object2ObjectMap<AEKey, BigInteger> copySignedNonZero(Object2ObjectMap<AEKey, BigInteger> source) {
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> copied = new Object2ObjectLinkedOpenHashMap<>();
        source.forEach((key, amount) -> {
            if (amount.signum() == 0) {
                throw new IllegalArgumentException("A Trinity joint cycle net amount must be non-zero");
            }
            copied.put(key, amount);
        });
        return FastUtilCollections.immutableMap(copied);
    }
}
