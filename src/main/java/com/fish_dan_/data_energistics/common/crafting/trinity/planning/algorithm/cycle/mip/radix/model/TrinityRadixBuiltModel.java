package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.radix.model;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.radix.codec.TrinityRadixLinearEncoder;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.radix.codec.TrinityRadixVariable;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;
import com.fish_dan_.data_energistics.util.FastUtilCollections;

import appeng.api.stacks.AEKey;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import org.ojalgo.optimisation.Variable;

import java.math.BigInteger;

/**
 * Couples one assembled ojAlgo model with the logical axes needed for exact decoding and objective search.
 *
 * @param model               radix encoder containing the complete copied solver model
 * @param firingVariables     stable firing axes
 * @param seedVariables       internal reserve axes
 * @param externalVariables   boundary reserve axes
 * @param objective           logical objective for the current pass
 * @param minimize            whether the objective is minimised rather than maximised
 * @param objectiveLowerBound exact certified lower bound
 * @param objectiveUpperBound exact certified upper bound
 */
public record TrinityRadixBuiltModel(
                                     TrinityRadixLinearEncoder model,
                                     Object2ObjectMap<TrinityPatternVariant, TrinityRadixVariable> firingVariables,
                                     Object2ObjectMap<AEKey, TrinityRadixVariable> seedVariables,
                                     Object2ObjectMap<AEKey, TrinityRadixVariable> externalVariables,
                                     TrinityRadixVariable objective,
                                     boolean minimize,
                                     BigInteger objectiveLowerBound,
                                     BigInteger objectiveUpperBound) {

    /**
     * Reconstructs all published logical values from solver digits using exact {@link BigInteger} arithmetic.
     */
    public TrinityRadixSolvedModel decode(Object2ObjectMap<Variable, BigInteger> values) {
        Object2ObjectLinkedOpenHashMap<TrinityPatternVariant, BigInteger> firings = new Object2ObjectLinkedOpenHashMap<>();
        firingVariables.forEach((variant, variable) -> putPositive(firings, variant, variable.decode(values)));
        return new TrinityRadixSolvedModel(
                FastUtilCollections.immutableMap(firings),
                decodePositive(seedVariables, values),
                decodePositive(externalVariables, values));
    }

    private static Object2ObjectMap<AEKey, BigInteger> decodePositive(
                                                                      Object2ObjectMap<AEKey, TrinityRadixVariable> variables,
                                                                      Object2ObjectMap<Variable, BigInteger> values) {
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> decoded = new Object2ObjectLinkedOpenHashMap<>();
        variables.forEach((key, variable) -> putPositive(decoded, key, variable.decode(values)));
        return FastUtilCollections.immutableMap(decoded);
    }

    private static <K> void putPositive(Object2ObjectMap<K, BigInteger> target, K key, BigInteger value) {
        if (value.signum() > 0) {
            target.put(key, value);
        }
    }
}
