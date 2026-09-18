package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.model.reservation;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.model.TrinityCycleFeasibilityRequest;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;

import appeng.api.stacks.AEKey;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMaps;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMaps;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.ojalgo.optimisation.Expression;
import org.ojalgo.optimisation.ExpressionsBasedModel;
import org.ojalgo.optimisation.Variable;

import java.math.BigInteger;
import java.util.Map;

/**
 * Eliminates monotone reserve axes from a request-owned, zero-objective feasibility model. With no fixed
 * reserve total, choosing every reserve at its cap preserves existence: reserves only have positive
 * coefficients in conservation and minimum-total rows. Reconstruction removes unnecessary stock before
 * exact verification and scheduling. Never apply this projection to a fixed-total or optimisation pass.
 */
public final class TrinityFeasibilityReserveProjection {

    private TrinityFeasibilityReserveProjection() {}

    /** Fixes bounded reserve axes and substitutes fixed terms before tightening integer rows by their GCD. */
    public static void apply(ExpressionsBasedModel model, Object2IntMap<AEKey> seedIndexes,
                             Object2IntMap<AEKey> externalIndexes) {
        fixReserves(model, seedIndexes);
        fixReserves(model, externalIndexes);
        for (Expression expression : model.getExpressions()) {
            BigInteger offset = BigInteger.ZERO;
            BigInteger divisor = BigInteger.ZERO;
            // set(..., 0) removes a term, so iterate a detached entry list.
            var terms = new ObjectArrayList<>(expression.getLinearEntrySet());
            for (var term : terms) {
                Variable variable = model.getVariable(term.getKey().index);
                BigInteger coefficient = term.getValue().toBigIntegerExact();
                if (variable.getLowerLimit().compareTo(variable.getUpperLimit()) == 0) {
                    offset = offset.add(coefficient.multiply(variable.getLowerLimit().toBigIntegerExact()));
                    expression.set(variable, BigInteger.ZERO);
                } else {
                    divisor = divisor.gcd(coefficient.abs());
                }
            }
            divisor = divisor.max(BigInteger.ONE);
            if (expression.getLowerLimit() != null) {
                BigInteger lower = expression.getLowerLimit().toBigIntegerExact().subtract(offset);
                expression.lower(floorDivide(lower.negate(), divisor).negate());
            }
            if (expression.getUpperLimit() != null) {
                expression.upper(floorDivide(expression.getUpperLimit().toBigIntegerExact().subtract(offset), divisor));
            }
            if (!divisor.equals(BigInteger.ONE)) {
                for (var term : new ObjectArrayList<>(expression.getLinearEntrySet())) {
                    expression.set(model.getVariable(term.getKey().index), term.getValue().toBigIntegerExact().divide(divisor));
                }
            }
        }
    }

    /**
     * Reconstructs reserve requirements from integer firings and the original final balances, then fills only
     * the mandatory total floor within the solver's caps. This proposes a candidate; the caller must still
     * verify conservation, real inventory, firing domains and scheduling before publishing it.
     */
    public static Object2ObjectMap<AEKey, BigInteger> reduce(
                                                            TrinityCycleFeasibilityRequest request,
                                                            Map<TrinityPatternVariant, BigInteger> firings,
                                                            Map<AEKey, BigInteger> caps,
                                                            BigInteger minimumTotal) {
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> result = new Object2ObjectLinkedOpenHashMap<>();
        BigInteger total = BigInteger.ZERO;
        for (AEKey key : caps.keySet()) {
            BigInteger net = BigInteger.ZERO;
            for (var firing : firings.entrySet()) {
                net = net.add(firing.getKey().netChange().getOrDefault(key, BigInteger.ZERO).multiply(firing.getValue()));
            }
            BigInteger required = request.demand().finalBalanceLowerBounds().getOrDefault(key, BigInteger.ZERO)
                    .subtract(net).max(BigInteger.ZERO);
            if (required.signum() > 0) result.put(key, required);
            total = total.add(required);
        }
        BigInteger remaining = minimumTotal.subtract(total);
        for (var cap : caps.entrySet()) {
            if (remaining.signum() <= 0) break;
            BigInteger current = result.getOrDefault(cap.getKey(), BigInteger.ZERO);
            BigInteger addition = cap.getValue().subtract(current).max(BigInteger.ZERO).min(remaining);
            if (addition.signum() > 0) result.put(cap.getKey(), current.add(addition));
            remaining = remaining.subtract(addition);
        }
        return Object2ObjectMaps.unmodifiable(result);
    }

    private static void fixReserves(ExpressionsBasedModel model, Object2IntMap<AEKey> indexes) {
        Object2IntMaps.fastForEach(indexes, entry -> {
            Variable variable = model.getVariable(entry.getIntValue());
            variable.level(variable.getUpperLimit());
        });
    }

    private static BigInteger floorDivide(BigInteger value, BigInteger positiveDivisor) {
        BigInteger[] parts = value.divideAndRemainder(positiveDivisor);
        return parts[1].signum() < 0 ? parts[0].subtract(BigInteger.ONE) : parts[0];
    }
}
