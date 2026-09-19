package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.bounds;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.model.TrinityCycleFeasibilityRequest;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.template.TrinityMipCoefficientTemplate.Coefficient;

import appeng.api.stacks.AEKey;

import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import org.jspecify.annotations.Nullable;

import java.math.BigInteger;

/**
 * Request-local exact necessary bounds, before any artificial firing envelope or floating-point solve.
 * Opposite proportional rows share one integer expression: its lower bound cannot exceed its upper bound.
 * This proves contradictions, never feasibility. No inventory or constraint is relaxed on the executable path.
 */
public final class TrinityExactCycleBalanceBounds {

    private TrinityExactCycleBalanceBounds() {}

    /** Returns true only for a contradiction certified with BigInteger over the original request. */
    public static boolean contradictory(TrinityCycleFeasibilityRequest request) {
        Object2ObjectOpenHashMap<Int2ObjectMap<BigInteger>, Interval> intervals = new Object2ObjectOpenHashMap<>();
        for (var bound : request.demand().requiredNetChangeLowerBounds().entrySet()) {
            if (!include(intervals, request, bound.getKey(), bound.getValue())) return true;
        }
        ObjectLinkedOpenHashSet<AEKey> keys = new ObjectLinkedOpenHashSet<>(request.coefficientTemplate().touchedKeys());
        keys.addAll(request.demand().finalBalanceLowerBounds().keySet());
        for (AEKey key : keys) {
            // Virtual shortage reserves and producible inputs have no finite inventory ceiling here.
            if (request.shortageDiagnostic() || request.producibleInputs().contains(key)) continue;
            BigInteger lower = request.demand().finalBalanceLowerBounds().getOrDefault(key, BigInteger.ZERO)
                    .subtract(request.available().getOrDefault(key, BigInteger.ZERO));
            if (!include(intervals, request, key, lower)) return true;
        }
        return false;
    }

    private static boolean include(Object2ObjectOpenHashMap<Int2ObjectMap<BigInteger>, Interval> intervals,
                                   TrinityCycleFeasibilityRequest request, AEKey key, BigInteger lower) {
        Int2ObjectLinkedOpenHashMap<BigInteger> row = new Int2ObjectLinkedOpenHashMap<>();
        BigInteger divisor = BigInteger.ZERO;
        for (Coefficient coefficient : request.coefficientTemplate().coefficients(key)) {
            var bounds = request.firingBounds().get(request.variants().get(coefficient.variantIndex()));
            if (bounds.fixed()) {
                lower = lower.subtract(coefficient.value().multiply(bounds.lowerInclusive()));
            } else {
                row.put(coefficient.variantIndex(), coefficient.value());
                divisor = divisor.gcd(coefficient.value().abs());
            }
        }
        if (row.isEmpty()) return lower.signum() <= 0;
        boolean positive = row.get(row.firstIntKey()).signum() > 0;
        BigInteger scale = positive ? divisor : divisor.negate();
        for (var entry : row.int2ObjectEntrySet()) entry.setValue(entry.getValue().divide(scale));
        Interval interval = intervals.computeIfAbsent(row, unused -> new Interval());
        BigInteger[] divided = lower.divideAndRemainder(divisor);
        BigInteger ceiling = divided[1].signum() > 0 ? divided[0].add(BigInteger.ONE) : divided[0];
        if (positive) interval.lower = interval.lower == null ? ceiling : interval.lower.max(ceiling);
        else interval.upper = interval.upper == null ? ceiling.negate() : interval.upper.min(ceiling.negate());
        return interval.lower == null || interval.upper == null || interval.lower.compareTo(interval.upper) <= 0;
    }

    private static final class Interval {
        private @Nullable BigInteger lower;
        private @Nullable BigInteger upper;
    }
}
