package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.model;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityPlanQuality;
import com.fish_dan_.data_energistics.util.FastUtilCollections;

import appeng.api.stacks.AEKey;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;

import java.math.BigInteger;

/**
 * Exact decoded result of all sequential feasibility objectives.
 *
 * @param firings          positive stable firing vector
 * @param modelSeed        positive conservation seed lower bounds
 * @param externalInputs   positive conservation boundary-input lower bounds
 * @param solverPasses     number of completed solver passes sharing one deadline
 * @param solverNanos      measured solver time
 * @param radix            whether the base-2^15 exact representation was required
 * @param quality          proof strength of the retained objective vector
 * @param actualInputs     diagnostic-only finite inventory allocated to required reserve
 * @param missingInputs    diagnostic-only virtual reserve absent from captured inventory
 * @param diagnosticStates actual solver calls charged to the local shortage search
 */
public record TrinityCycleFeasibilitySolution(
                                              Object2ObjectMap<TrinityPatternVariant, BigInteger> firings,
                                              Object2ObjectMap<AEKey, BigInteger> modelSeed,
                                              Object2ObjectMap<AEKey, BigInteger> externalInputs,
                                              int solverPasses,
                                              long solverNanos,
                                              boolean radix,
                                              TrinityPlanQuality quality,
                                              Object2ObjectMap<AEKey, BigInteger> actualInputs,
                                              Object2ObjectMap<AEKey, BigInteger> missingInputs,
                                              int diagnosticStates) {

    /**
     * Copies decoded values before exact conservation and scheduling consume them.
     */
    public TrinityCycleFeasibilitySolution {
        if (firings.isEmpty() || solverPasses <= 0 || solverNanos < 0L || diagnosticStates < 0) {
            throw new IllegalArgumentException("A Trinity feasibility solution requires complete exact accounting");
        }
        firings = copyPositiveFirings(firings);
        modelSeed = copyPositiveAmounts(modelSeed, "seed");
        externalInputs = copyPositiveAmounts(externalInputs, "external input");
        actualInputs = copyPositiveAmounts(actualInputs, "actual input");
        missingInputs = copyPositiveAmounts(missingInputs, "missing input");
    }

    /**
     * @return exact positive reserve required by the diagnostic firing vector
     */
    public Object2ObjectMap<AEKey, BigInteger> requiredInputs() {
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> required = new Object2ObjectLinkedOpenHashMap<>(externalInputs);
        modelSeed.forEach((key, amount) -> required.merge(key, amount, BigInteger::add));
        return FastUtilCollections.immutableMap(required);
    }

    /**
     * @return exact model external-input objective
     */
    public BigInteger externalTotal() {
        return total(externalInputs);
    }

    /**
     * @return exact model seed objective
     */
    public BigInteger seedTotal() {
        return total(modelSeed);
    }

    /**
     * @return exact logical firing objective
     */
    public BigInteger firingTotal() {
        return total(firings);
    }

    private static Object2ObjectMap<TrinityPatternVariant, BigInteger> copyPositiveFirings(
                                                                                           Object2ObjectMap<TrinityPatternVariant, BigInteger> source) {
        Object2ObjectLinkedOpenHashMap<TrinityPatternVariant, BigInteger> copied = new Object2ObjectLinkedOpenHashMap<>();
        source.forEach((variant, amount) -> {
            if (amount.signum() <= 0) {
                throw new IllegalArgumentException("A Trinity feasibility firing count must be positive");
            }
            copied.put(variant, amount);
        });
        return FastUtilCollections.immutableMap(copied);
    }

    private static Object2ObjectMap<AEKey, BigInteger> copyPositiveAmounts(Object2ObjectMap<AEKey, BigInteger> source, String role) {
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> copied = new Object2ObjectLinkedOpenHashMap<>();
        source.forEach((key, amount) -> {
            if (amount.signum() <= 0) {
                throw new IllegalArgumentException("A Trinity feasibility " + role + " must be positive");
            }
            copied.put(key, amount);
        });
        return FastUtilCollections.immutableMap(copied);
    }

    private static BigInteger total(Object2ObjectMap<?, BigInteger> amounts) {
        return amounts.values().stream().reduce(BigInteger.ZERO, BigInteger::add);
    }
}
