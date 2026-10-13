package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.schedule.TrinityCompressedSchedule;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.schedule.TrinityVariantFiring;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;
import com.fish_dan_.data_energistics.util.FastUtilCollections;

import appeng.api.stacks.AEKey;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectList;

import java.math.BigInteger;

/**
 * Closed-form deterministic production cycle and its exact compressed execution proof.
 *
 * @param oneCycleOrder    stable firing blocks in one logical production cycle
 * @param repetitions      compact complete-cycle count
 * @param aggregateFirings total logical firing vector
 * @param minimumSeed      exact maximum prefix deficit across all repetitions
 * @param initialInputs    exact inventory that must be reserved, including final-total target contribution
 * @param netChange        exact signed effect across all repetitions
 * @param schedule         executable compressed batch proof
 */
public record TrinityCyclePlan(
                               ObjectList<TrinityVariantFiring> oneCycleOrder,
                               BigInteger repetitions,
                               Object2ObjectMap<TrinityPatternVariant, BigInteger> aggregateFirings,
                               Object2ObjectMap<AEKey, BigInteger> minimumSeed,
                               Object2ObjectMap<AEKey, BigInteger> initialInputs,
                               Object2ObjectMap<AEKey, BigInteger> netChange,
                               TrinityCompressedSchedule schedule) {

    /**
     * Copies the complete cycle accounting and rejects an inconsistent firing vector.
     */
    public TrinityCyclePlan {
        if (oneCycleOrder.isEmpty() || repetitions.signum() <= 0) {
            throw new IllegalArgumentException("A Trinity cycle plan requires complete positive accounting");
        }
        oneCycleOrder = FastUtilCollections.immutableList(oneCycleOrder);
        aggregateFirings = copyPositiveFirings(aggregateFirings);
        minimumSeed = copyPositiveAmounts(minimumSeed);
        initialInputs = copyPositiveAmounts(initialInputs);
        netChange = copySignedNonZero(netChange);
        Object2ObjectLinkedOpenHashMap<TrinityPatternVariant, BigInteger> expected = new Object2ObjectLinkedOpenHashMap<>();
        for (TrinityVariantFiring firing : oneCycleOrder) {
            expected.merge(firing.variant(), firing.count().multiply(repetitions), BigInteger::add);
        }
        if (!expected.equals(aggregateFirings) || !schedule.aggregateFirings().equals(aggregateFirings)) {
            throw new IllegalArgumentException("A Trinity cycle schedule must match its compact firing vector");
        }
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> calculatedNet = new Object2ObjectLinkedOpenHashMap<>();
        aggregateFirings.forEach((variant, count) -> variant.netChange().forEach(
                (key, amount) -> calculatedNet.merge(key, amount.multiply(count), BigInteger::add)));
        calculatedNet.entrySet().removeIf(entry -> entry.getValue().signum() == 0);
        if (!calculatedNet.equals(netChange)) {
            throw new IllegalArgumentException("A Trinity cycle net change must equal its exact firing effects");
        }
        for (Object2ObjectMap.Entry<AEKey, BigInteger> seed : minimumSeed.object2ObjectEntrySet()) {
            if (initialInputs.getOrDefault(seed.getKey(), BigInteger.ZERO).compareTo(seed.getValue()) < 0) {
                throw new IllegalArgumentException("A Trinity cycle initial input must include every minimum seed");
            }
        }
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> calculatedFinal = new Object2ObjectLinkedOpenHashMap<>(initialInputs);
        netChange.forEach((key, amount) -> calculatedFinal.merge(key, amount, BigInteger::add));
        if (calculatedFinal.values().stream().anyMatch(amount -> amount.signum() < 0)) {
            throw new IllegalArgumentException("A Trinity cycle final balance cannot be negative");
        }
        calculatedFinal.entrySet().removeIf(entry -> entry.getValue().signum() == 0);
        if (!calculatedFinal.equals(schedule.finalBalances())) {
            throw new IllegalArgumentException("A Trinity cycle schedule must conserve its exact final balances");
        }
    }

    private static Object2ObjectMap<TrinityPatternVariant, BigInteger> copyPositiveFirings(
                                                                                           Object2ObjectMap<TrinityPatternVariant, BigInteger> source) {
        Object2ObjectLinkedOpenHashMap<TrinityPatternVariant, BigInteger> copied = new Object2ObjectLinkedOpenHashMap<>();
        source.forEach((variant, count) -> {
            if (count.signum() <= 0) {
                throw new IllegalArgumentException("A Trinity cycle firing count must be positive");
            }
            copied.put(variant, count);
        });
        return FastUtilCollections.immutableMap(copied);
    }

    private static Object2ObjectMap<AEKey, BigInteger> copyPositiveAmounts(Object2ObjectMap<AEKey, BigInteger> source) {
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> copied = new Object2ObjectLinkedOpenHashMap<>();
        source.forEach((key, amount) -> {
            if (amount.signum() <= 0) {
                throw new IllegalArgumentException("A Trinity cycle input amount must be positive");
            }
            copied.put(key, amount);
        });
        return FastUtilCollections.immutableMap(copied);
    }

    private static Object2ObjectMap<AEKey, BigInteger> copySignedNonZero(Object2ObjectMap<AEKey, BigInteger> source) {
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> copied = new Object2ObjectLinkedOpenHashMap<>();
        source.forEach((key, amount) -> {
            if (amount.signum() == 0) {
                throw new IllegalArgumentException("A Trinity cycle net amount must be non-zero");
            }
            copied.put(key, amount);
        });
        return FastUtilCollections.immutableMap(copied);
    }
}
