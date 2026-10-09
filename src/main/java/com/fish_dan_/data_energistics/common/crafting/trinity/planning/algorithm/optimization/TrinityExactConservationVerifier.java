package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.optimization;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnostic;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnosticCode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;
import com.fish_dan_.data_energistics.util.FastUtilCollections;

import appeng.api.stacks.AEKey;

import net.minecraft.network.chat.Component;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectList;

import java.math.BigInteger;

/**
 * Recomputes every solver balance and bound with BigInteger before one result can constrain the next pass.
 * <p>
 * Exact rejection boundary for integral-looking ojAlgo results that violate a model equation or bound.
 */
public final class TrinityExactConservationVerifier {

    /**
     * @return stateless exact verifier
     */
    public static TrinityExactConservationVerifier create() {
        return new TrinityExactConservationVerifier();
    }

    /**
     * @param variants                     complete model transition set
     * @param firings                      exact non-zero firing values
     * @param initialInputs                exact seed and external values
     * @param upperBounds                  finite input upper bounds; absent keys are intentionally
     *                                     unbounded/upstream-craftable
     * @param finalLowerBounds             required final balance per key
     * @param requiredNetChangeLowerBounds required net effects independent of seed
     * @return recomputed signed net change or {@code MIP_INEXACT_RESULT}
     */
    public TrinityAlgorithmResult<Object2ObjectMap<AEKey, BigInteger>> verify(
                                                                              ObjectList<TrinityPatternVariant> variants,
                                                                              Object2ObjectMap<TrinityPatternVariant, BigInteger> firings,
                                                                              Object2ObjectMap<AEKey, BigInteger> initialInputs,
                                                                              Object2ObjectMap<AEKey, BigInteger> upperBounds,
                                                                              Object2ObjectMap<AEKey, BigInteger> finalLowerBounds,
                                                                              Object2ObjectMap<AEKey, BigInteger> requiredNetChangeLowerBounds) {
        if (requiredNetChangeLowerBounds.isEmpty()) {
            throw new IllegalArgumentException(
                    "A Trinity conservation verification requires complete inputs and positive net bounds");
        }

        ObjectLinkedOpenHashSet<TrinityPatternVariant> legalVariants = new ObjectLinkedOpenHashSet<>(variants);
        validatePositiveBounds(finalLowerBounds, "final");
        validatePositiveBounds(requiredNetChangeLowerBounds, "net change");
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> net = new Object2ObjectLinkedOpenHashMap<>();
        for (Object2ObjectMap.Entry<TrinityPatternVariant, BigInteger> firing : firings.object2ObjectEntrySet()) {
            if (!legalVariants.contains(firing.getKey()) || firing.getValue().signum() <= 0) {
                return inexact("firing_domain", firing.getKey().toString());
            }
            firing.getKey().netChange().forEach((key, amount) -> net.merge(key, amount.multiply(firing.getValue()), BigInteger::add));
        }
        net.entrySet().removeIf(entry -> entry.getValue().signum() == 0);
        for (Object2ObjectMap.Entry<AEKey, BigInteger> bound : requiredNetChangeLowerBounds.object2ObjectEntrySet()) {
            BigInteger actual = net.getOrDefault(bound.getKey(), BigInteger.ZERO);
            if (actual.compareTo(bound.getValue()) < 0) {
                return inexact("required_net", bound.getKey() + ":" + actual + "<" + bound.getValue());
            }
        }

        for (Object2ObjectMap.Entry<AEKey, BigInteger> input : initialInputs.object2ObjectEntrySet()) {
            if (input.getValue().signum() < 0) {
                return inexact("input_domain", String.valueOf(input.getValue()));
            }
            BigInteger upper = upperBounds.get(input.getKey());
            if (upper != null && input.getValue().compareTo(upper) > 0) {
                return inexact("input_upper", input.getValue() + ">" + upper);
            }
        }
        for (Object2ObjectMap.Entry<AEKey, BigInteger> bound : upperBounds.object2ObjectEntrySet()) {
            if (bound.getValue().signum() < 0) {
                throw new IllegalArgumentException("A Trinity exact upper bound cannot be negative");
            }
        }

        ObjectLinkedOpenHashSet<AEKey> keys = new ObjectLinkedOpenHashSet<>(net.keySet());
        keys.addAll(initialInputs.keySet());
        keys.addAll(finalLowerBounds.keySet());
        keys.addAll(requiredNetChangeLowerBounds.keySet());
        for (AEKey key : keys) {
            BigInteger finalBalance = initialInputs.getOrDefault(key, BigInteger.ZERO)
                    .add(net.getOrDefault(key, BigInteger.ZERO));
            BigInteger lower = finalLowerBounds.getOrDefault(key, BigInteger.ZERO);
            if (finalBalance.compareTo(lower) < 0) {
                return inexact("conservation", key + ":" + finalBalance + "<" + lower);
            }
        }
        return TrinityAlgorithmResult.success(FastUtilCollections.immutableMap(net));
    }

    private static void validatePositiveBounds(Object2ObjectMap<AEKey, BigInteger> bounds, String description) {
        bounds.forEach((key, amount) -> {
            if (amount.signum() <= 0) {
                throw new IllegalArgumentException("A Trinity exact " + description + " bound must be positive");
            }
        });
    }

    private static <T> TrinityAlgorithmResult<T> inexact(String constraint, String value) {
        return TrinityAlgorithmResult.failure(new TrinityPlanningDiagnostic(
                TrinityPlanningDiagnosticCode.MIP_INEXACT_RESULT,
                Component.translatable("gui.data_energistics.trinity_planning.diagnostic.inexact_result"),
                FastUtilCollections.mapOf("constraint", constraint, "value", value)));
    }
}
