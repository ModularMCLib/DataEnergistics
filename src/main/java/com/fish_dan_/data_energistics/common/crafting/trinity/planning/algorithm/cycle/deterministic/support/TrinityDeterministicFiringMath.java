package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.deterministic.support;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.schedule.TrinityVariantFiring;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;

import appeng.api.stacks.AEKey;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;

import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Provides the single exact BigInteger implementation of firing-vector arithmetic shared by deterministic stages.
 */
public final class TrinityDeterministicFiringMath {

    public static final BigInteger ZERO = BigInteger.ZERO;

    private TrinityDeterministicFiringMath() {}

    public static Object2ObjectLinkedOpenHashMap<TrinityPatternVariant, BigInteger> aggregate(List<TrinityVariantFiring> order) {
        Object2ObjectLinkedOpenHashMap<TrinityPatternVariant, BigInteger> aggregate = new Object2ObjectLinkedOpenHashMap<>();
        order.forEach(firing -> aggregate.merge(firing.variant(), firing.count(), BigInteger::add));
        return aggregate;
    }

    public static Object2ObjectLinkedOpenHashMap<TrinityPatternVariant, BigInteger> aggregateRepeated(
                                                                                                      Map<TrinityPatternVariant, BigInteger> primitive,
                                                                                                      BigInteger repetitions,
                                                                                                      Map<TrinityPatternVariant, BigInteger> residual) {
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

    public static Map<AEKey, BigInteger> netChange(Map<TrinityPatternVariant, BigInteger> firings) {
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> net = new Object2ObjectLinkedOpenHashMap<>();
        firings.forEach((variant, count) -> variant.netChange().forEach(
                (key, amount) -> net.merge(key, amount.multiply(count), BigInteger::add)));
        net.entrySet().removeIf(entry -> entry.getValue().signum() == 0);
        return Collections.unmodifiableMap(net);
    }

    public static Map<AEKey, BigInteger> multiplySigned(
                                                        Map<AEKey, BigInteger> amounts,
                                                        BigInteger multiplier) {
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> multiplied = new Object2ObjectLinkedOpenHashMap<>();
        amounts.forEach((key, amount) -> {
            BigInteger result = amount.multiply(multiplier);
            if (result.signum() != 0) {
                multiplied.put(key, result);
            }
        });
        return Collections.unmodifiableMap(multiplied);
    }

    public static Map<AEKey, BigInteger> addSigned(
                                                   Map<AEKey, BigInteger> first,
                                                   Map<AEKey, BigInteger> second) {
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> result = new Object2ObjectLinkedOpenHashMap<>(first);
        second.forEach((key, amount) -> result.merge(key, amount, BigInteger::add));
        result.entrySet().removeIf(entry -> entry.getValue().signum() == 0);
        return Collections.unmodifiableMap(result);
    }

    public static BigInteger sum(Map<?, BigInteger> amounts) {
        return amounts.values().stream().reduce(ZERO, BigInteger::add);
    }

    public static BigInteger ceilDivide(BigInteger numerator, BigInteger denominator) {
        if (numerator.signum() <= 0 || denominator.signum() <= 0) {
            throw new IllegalArgumentException("A deterministic Trinity ratio requires positive values");
        }
        BigInteger[] division = numerator.divideAndRemainder(denominator);
        return division[1].signum() == 0 ? division[0] : division[0].add(BigInteger.ONE);
    }
}
