package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.bounds;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.model.TrinityCycleFeasibilityRequest;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.model.TrinityFiringBounds;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.template.TrinityMipCoefficientTemplate.Coefficient;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;
import com.fish_dan_.data_energistics.utils.FastUtilCollections;

import appeng.api.stacks.AEKey;

import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import org.jspecify.annotations.Nullable;

import java.math.BigInteger;
import java.util.Objects;

/**
 * Request-local exact necessary bounds, before any artificial firing envelope or floating-point solve.
 * Opposite proportional rows share one integer expression: its lower bound cannot exceed its upper bound.
 * Material balances can also bound a firing count used by a different row. Every propagated bound is necessary,
 * so a contradiction rules out the original request even when some firing counts are unbounded.
 */
public final class TrinityExactCycleBalanceBounds {

    private static final int MAX_PROPAGATION_PASSES = 4;

    private TrinityExactCycleBalanceBounds() {}

    /** Returns true only for a contradiction certified with BigInteger over the original request. */
    public static boolean contradictory(TrinityCycleFeasibilityRequest request) {
        return inferFiringBounds(request) == null;
    }

    /**
     * Reuses the exact lower firing bounds as a shortage candidate if they already satisfy all net
     * production rows. Virtual reserves and an executable order still have to be checked by the caller.
     */
    public static Object2ObjectMap<TrinityPatternVariant, BigInteger> shortageFirings(TrinityCycleFeasibilityRequest request) {
        FiringIntervals firing = inferFiringBounds(request);
        if (firing == null) return FastUtilCollections.mapOf();
        for (var bound : request.demand().requiredNetChangeLowerBounds().entrySet()) {
            BigInteger net = BigInteger.ZERO;
            for (Coefficient coefficient : request.coefficientTemplate().coefficients(bound.getKey())) {
                net = net.add(coefficient.value().multiply(firing.lower[coefficient.variantIndex()]));
            }
            if (net.compareTo(bound.getValue()) < 0) return FastUtilCollections.mapOf();
        }
        Object2ObjectLinkedOpenHashMap<TrinityPatternVariant, BigInteger> result = new Object2ObjectLinkedOpenHashMap<>();
        BigInteger total = BigInteger.ZERO;
        for (int index = 0; index < firing.lower.length; index++) {
            BigInteger amount = firing.lower[index];
            if (amount.signum() > 0) {
                result.put(request.variants().get(index), amount);
                total = total.add(amount);
            }
        }
        return total.compareTo(request.firingLowerBound().max(BigInteger.ONE)) >= 0 ? result : FastUtilCollections.mapOf();
    }

    private static @Nullable FiringIntervals inferFiringBounds(TrinityCycleFeasibilityRequest request) {
        Object2ObjectLinkedOpenHashMap<Int2ObjectMap<BigInteger>, Interval> intervals = new Object2ObjectLinkedOpenHashMap<>();
        for (var bound : request.demand().requiredNetChangeLowerBounds().entrySet()) {
            if (!include(intervals, request, bound.getKey(), bound.getValue())) return null;
        }
        ObjectLinkedOpenHashSet<AEKey> keys = new ObjectLinkedOpenHashSet<>(request.coefficientTemplate().touchedKeys());
        keys.addAll(request.demand().finalBalanceLowerBounds().keySet());
        for (AEKey key : keys) {
            // Virtual shortage reserves and producible inputs have no finite inventory ceiling here.
            if (request.shortageDiagnostic() || request.producibleInputs().contains(key)) continue;
            BigInteger lower = request.demand().finalBalanceLowerBounds().getOrDefault(key, BigInteger.ZERO)
                    .subtract(request.available().getOrDefault(key, BigInteger.ZERO));
            if (!include(intervals, request, key, lower)) return null;
        }
        FiringIntervals firing = new FiringIntervals(request);
        // A feedback loop can keep tightening a bound by one. Leave further work to the solver.
        for (int pass = 0; pass < MAX_PROPAGATION_PASSES; pass++) {
            firing.changed = false;
            for (var entry : intervals.object2ObjectEntrySet()) {
                Interval interval = entry.getValue();
                if (interval.lower != null && firing.contradicts(entry.getKey(), interval.lower, false)) return null;
                if (interval.upper != null && firing.contradicts(entry.getKey(), interval.upper.negate(), true)) return null;
            }
            if (!firing.changed) break;
        }
        return firing;
    }

    private static boolean include(Object2ObjectLinkedOpenHashMap<Int2ObjectMap<BigInteger>, Interval> intervals,
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
        BigInteger ceiling = ceilDivide(lower, divisor);
        if (positive) interval.lower = interval.lower == null ? ceiling : interval.lower.max(ceiling);
        else interval.upper = interval.upper == null ? ceiling.negate() : interval.upper.min(ceiling.negate());
        return interval.lower == null || interval.upper == null || interval.lower.compareTo(interval.upper) <= 0;
    }

    private static BigInteger ceilDivide(BigInteger numerator, BigInteger positiveDenominator) {
        BigInteger[] divided = numerator.divideAndRemainder(positiveDenominator);
        return divided[1].signum() > 0 ? divided[0].add(BigInteger.ONE) : divided[0];
    }

    private static final class FiringIntervals {

        private final BigInteger[] lower;
        private final @Nullable BigInteger[] upper;
        private boolean changed;

        private FiringIntervals(TrinityCycleFeasibilityRequest request) {
            int count = request.variants().size();
            this.lower = new BigInteger[count];
            this.upper = new BigInteger[count];
            for (int index = 0; index < count; index++) {
                TrinityFiringBounds bounds = request.firingBounds().get(request.variants().get(index));
                this.lower[index] = bounds.lowerInclusive();
                this.upper[index] = bounds.upperInclusive().orElse(null);
            }
        }

        /** Tightens firing ranges from one inequality written as a weighted sum >= required. */
        private boolean contradicts(Int2ObjectMap<BigInteger> row, BigInteger required, boolean reverse) {
            BigInteger maximum = BigInteger.ZERO;
            int unbounded = -1;
            for (var term : row.int2ObjectEntrySet()) {
                int index = term.getIntKey();
                BigInteger coefficient = reverse ? term.getValue().negate() : term.getValue();
                BigInteger endpoint = coefficient.signum() > 0 ? this.upper[index] : this.lower[index];
                if (endpoint == null) {
                    if (unbounded >= 0) return false;
                    unbounded = index;
                } else {
                    maximum = maximum.add(coefficient.multiply(endpoint));
                }
            }
            if (unbounded >= 0) {
                BigInteger coefficient = row.get(unbounded).abs();
                tightenLower(unbounded, ceilDivide(required.subtract(maximum), coefficient));
                return false;
            }
            BigInteger slack = maximum.subtract(required);
            if (slack.signum() < 0) return true;

            // Each firing may move away from its maximizing endpoint only while the row has slack.
            for (var term : row.int2ObjectEntrySet()) {
                int index = term.getIntKey();
                BigInteger coefficient = reverse ? term.getValue().negate() : term.getValue();
                BigInteger distance = slack.divide(coefficient.abs());
                if (coefficient.signum() > 0) {
                    tightenLower(index, Objects.requireNonNull(this.upper[index]).subtract(distance));
                } else {
                    BigInteger cap = this.lower[index].add(distance);
                    BigInteger previous = this.upper[index];
                    if (previous == null || cap.compareTo(previous) < 0) {
                        this.upper[index] = cap;
                        this.changed = true;
                    }
                }
            }
            return false;
        }

        private void tightenLower(int index, BigInteger minimum) {
            if (minimum.compareTo(this.lower[index]) > 0) {
                this.lower[index] = minimum;
                this.changed = true;
            }
        }
    }

    private static final class Interval {

        private @Nullable BigInteger lower;
        private @Nullable BigInteger upper;
    }
}
