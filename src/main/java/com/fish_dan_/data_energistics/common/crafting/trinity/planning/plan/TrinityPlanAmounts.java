package com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan;

import com.fish_dan_.data_energistics.util.FastUtilCollections;

import appeng.api.stacks.AEKey;

import it.unimi.dsi.fastutil.objects.Object2ObjectMap;

import java.math.BigInteger;

/**
 * Centralizes exact amount validation and AE2 boundary conversion for immutable planning values.
 */
final class TrinityPlanAmounts {

    private TrinityPlanAmounts() {}

    static Object2ObjectMap<AEKey, BigInteger> validatePositive(Object2ObjectMap<AEKey, BigInteger> source, String role) {
        return validate(source, role, false);
    }

    static Object2ObjectMap<AEKey, BigInteger> validateSignedNonZero(Object2ObjectMap<AEKey, BigInteger> source, String role) {
        return validate(source, role, true);
    }

    private static Object2ObjectMap<AEKey, BigInteger> validate(
                                                                Object2ObjectMap<AEKey, BigInteger> source,
                                                                String role,
                                                                boolean signed) {
        source.forEach((key, amount) -> {
            if (amount.signum() == 0 || (!signed && amount.signum() < 0)) {
                throw new IllegalArgumentException("Trinity " + role + " must contain non-zero valid amounts");
            }
        });
        return FastUtilCollections.immutableMap(source);
    }
}
