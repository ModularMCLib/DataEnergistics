package com.fish_dan_.data_energistics.api.crafting.dispatch;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.KeyCounter;

import org.jspecify.annotations.Nullable;

import java.math.BigInteger;

/**
 * Optional exact-count extension for an existing counted provider adapter. All calls run on the server thread.
 * The adapter must publish targeted capacity with a stable, provider-independent physical machine identity.
 * Trinity selects and exclusively reserves that machine through its normal proposal pipeline before offering
 * an exact batch; this method rechecks actual capacity without reserving or transferring any inputs.
 * Existing long preparation remains available to legacy callers and targets without an exclusive reservation.
 */
public interface BigIntegerCraftingProviderAdapter extends CountedCraftingProviderAdapter {

    /**
     * Prepares a complete homogeneous batch for the exclusively selected physical target.
     * The original pattern and exact per-slot prototype are read-only; no state changes are permitted here.
     * Return null when no batch is currently acceptable, otherwise admit a count in 1..requestedCount.
     * Capacity and ownership must include all copies even when their total material exceeds Long.MAX_VALUE.
     */
    @Nullable
    BigIntegerCraftingAdmission prepareBigIntegerBatch(IPatternDetails pattern, KeyCounter[] prototype,
                                                       BigInteger requestedCount, CountedCraftingTarget target);
}
