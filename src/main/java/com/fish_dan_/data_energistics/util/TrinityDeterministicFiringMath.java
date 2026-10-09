package com.fish_dan_.data_energistics.util;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.schedule.TrinityVariantFiring;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;

import appeng.api.stacks.AEKey;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectList;

import java.math.BigInteger;

/**
 * Provides the single exact BigInteger implementation of firing-vector arithmetic shared by deterministic stages.
 */
public final class TrinityDeterministicFiringMath {

    public static final BigInteger ZERO = BigInteger.ZERO;

    private TrinityDeterministicFiringMath() {}

    public static Object2ObjectLinkedOpenHashMap<TrinityPatternVariant, BigInteger> aggregate(ObjectList<TrinityVariantFiring> order) {
        Object2ObjectLinkedOpenHashMap<TrinityPatternVariant, BigInteger> aggregate = new Object2ObjectLinkedOpenHashMap<>();
        order.forEach(firing -> aggregate.merge(firing.variant(), firing.count(), BigInteger::add));
        return aggregate;
    }

    public static Object2ObjectLinkedOpenHashMap<TrinityPatternVariant, BigInteger> aggregateRepeated(
                                                                                                      Object2ObjectMap<TrinityPatternVariant, BigInteger> primitive,
                                                                                                      BigInteger repetitions,
                                                                                                      Object2ObjectMap<TrinityPatternVariant, BigInteger> residual) {
        Object2ObjectLinkedOpenHashMap<TrinityPatternVariant, BigInteger> aggregate = new Object2ObjectLinkedOpenHashMap<>();
        primitive.forEach((variant, count) -> {
            BigInteger repeated = count.multiply(repetitions);
            if (repeated.signum() > 0) {
                aggregate.put(variant, repeated);
            }
        });
        residual.forEach((variant, count) -> aggregate.merge(variant, count, BigInteger::add));
        return aggregate;
    }

    public static Object2ObjectMap<AEKey, BigInteger> netChange(Object2ObjectMap<TrinityPatternVariant, BigInteger> firings) {
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> net = new Object2ObjectLinkedOpenHashMap<>();
        firings.forEach((variant, count) -> variant.netChange().forEach(
                (key, amount) -> net.merge(key, amount.multiply(count), BigInteger::add)));
        net.entrySet().removeIf(entry -> entry.getValue().signum() == 0);
        return FastUtilCollections.immutableMap(net);
    }

    public static Object2ObjectMap<AEKey, BigInteger> multiplySigned(
                                                                     Object2ObjectMap<AEKey, BigInteger> amounts,
                                                                     BigInteger multiplier) {
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> multiplied = new Object2ObjectLinkedOpenHashMap<>();
        amounts.forEach((key, amount) -> {
            BigInteger result = amount.multiply(multiplier);
            if (result.signum() != 0) {
                multiplied.put(key, result);
            }
        });
        return FastUtilCollections.immutableMap(multiplied);
    }

    public static Object2ObjectMap<AEKey, BigInteger> addSigned(
                                                                Object2ObjectMap<AEKey, BigInteger> first,
                                                                Object2ObjectMap<AEKey, BigInteger> second) {
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> result = new Object2ObjectLinkedOpenHashMap<>(first);
        second.forEach((key, amount) -> result.merge(key, amount, BigInteger::add));
        result.entrySet().removeIf(entry -> entry.getValue().signum() == 0);
        return FastUtilCollections.immutableMap(result);
    }

    public static BigInteger sum(Object2ObjectMap<?, BigInteger> amounts) {
        return amounts.values().stream().reduce(ZERO, BigInteger::add);
    }
}
