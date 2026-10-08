package com.fish_dan_.data_energistics.api.crafting.dispatch;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.KeyCounter;

import it.unimi.dsi.fastutil.objects.ObjectList;
import org.jspecify.annotations.Nullable;

/**
 * Optional server-thread adapter for one provider instance to admit counted Trinity dispatches.
 *
 * <p>
 * Preparation is read-only: it may inspect current provider state but must not consume inputs, reserve capacity or
 * mutate the supplied prototype. Target selection, retry windows and planning remain private to DataEnergistics.
 * </p>
 */
public interface CountedCraftingProviderAdapter {

    /**
     * Prepares one physical submission containing up to the requested number of identical logical crafts.
     *
     * <p>
     * {@code requestedCount} is always positive. A returned admission must report a count in the inclusive range
     * {@code 1..requestedCount}; violating this contract fails the current provider attempt. Returning {@code null}
     * means that the provider cannot currently accept any craft.
     * </p>
     *
     * @param patternDetails exact pattern selected by the crafting plan
     * @param prototype      read-only, exact per-craft input prototype for every pattern input slot
     * @param requestedCount positive maximum logical craft count available to this provider
     * @return one one-shot admission, or {@code null} when no craft can currently be accepted
     */
    @Nullable
    CountedCraftingAdmission prepareBatch(
                                          IPatternDetails patternDetails,
                                          KeyCounter[] prototype,
                                          long requestedCount);

    /**
     * Captures every currently usable dispatch target without reserving capacity, consuming inputs or advancing a
     * provider routing cursor. An empty result means that this adapter does not expose a targeted capacity view.
     */
    default ObjectList<CountedCraftingCapacity> captureCapacityFast(
                                                                    IPatternDetails patternDetails, KeyCounter[] prototype, long requestedCount) {
        return ObjectList.of();
    }

    /**
     * Prepares one counted submission for the exact target selected from {@link #captureCapacityFast}.
     *
     * @param patternDetails exact pattern selected by the crafting plan
     * @param prototype      read-only exact per-craft input prototype
     * @param requestedCount positive maximum logical craft count offered to this target
     * @param target         exact previously captured target
     * @return one one-shot admission, or {@code null} when the target is no longer available
     */
    @Nullable
    CountedCraftingAdmission prepareBatchForTarget(
                                                   IPatternDetails patternDetails,
                                                   KeyCounter[] prototype,
                                                   long requestedCount,
                                                   CountedCraftingTarget target);
}
