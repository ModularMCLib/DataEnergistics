package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.governor;

import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.budget.CraftingDispatchLimits;

import java.util.function.LongSupplier;

/**
 * Server-wide current-tick admission boundary shared by every Trinity grid.
 *
 * <p>
 * Production admission uses a physical-call quota planned from the previous complete Minecraft tick. Current
 * capture, submission and recovery durations are observations for the next tick, never a current-tick deadline.
 * </p>
 */
public interface CraftingServerDispatchBudget {

    /**
     * Creates the lifecycle-owned server budget.
     *
     * @param nanoClock              monotonic nanosecond source
     * @param targetTickNanos        desired complete server tick ceiling
     * @param overloadedTrickleNanos dispatch work retained when the non-Trinity baseline already reached the ceiling
     * @return mutable server-thread-confined budget
     */
    static CraftingServerDispatchBudget create(
                                               LongSupplier nanoClock,
                                               long targetTickNanos,
                                               long overloadedTrickleNanos) {
        return new MeasuredCraftingServerDispatchBudget(nanoClock, targetTickNanos, overloadedTrickleNanos);
    }

    /**
     * @return a boundary used before server tick sampling starts
     */
    static CraftingServerDispatchBudget unbounded() {
        return MeasuredCraftingServerDispatchBudget.UNBOUNDED;
    }

    /**
     * Checks whether another irreversible provider call may begin.
     *
     * @param activeDispatchNanos non-negative elapsed work retained for compatibility; production quota admission
     *                            does not use current elapsed time
     * @return whether the quota computed before this tick still has a physical call available
     */
    boolean canStart(long activeDispatchNanos);

    /**
     * Allows an already-started capture or submission to reach the first physical call even if preparation exhausted
     * the measured time allowance. Only the server thread may query this boundary; it does not mutate the budget.
     *
     * @return whether the first physical attempt has not yet started in this tick
     */
    default boolean canCompleteFirstAttempt() {
        return false;
    }

    /**
     * Records final admission immediately before the provider call. Only the server thread may call it, and each
     * admitted physical attempt must be recorded even if the provider later rejects the input.
     */
    default void recordPhysicalAttempt() {}

    /**
     * Accounts completed server-thread dispatch work.
     *
     * @param elapsedNanos non-negative measured duration
     */
    void record(long elapsedNanos);

    /**
     * Records completed capacity work separately so its configured time ceiling can become a next-tick physical quota.
     *
     * @param elapsedNanos non-negative duration; must be called exactly once per completed capture on the server thread
     */
    default void recordCapacityCapture(long elapsedNanos) {
        record(elapsedNanos);
    }

    /**
     * @return whether current work is controlled by the precomputed quota rather than live elapsed time; stable for
     *         the lifetime of this server-thread boundary
     */
    default boolean usesHistoricalQuota() {
        return false;
    }

    /**
     * Converts configured Grid time ceilings to physical-call limits using only completed history, without consuming
     * quota.
     *
     * @param limits non-null configured ceilings captured before the Grid starts dispatching
     * @return immutable positive physical limits for that Grid window; never exceeds configured call ceilings
     */
    default CraftingDispatchLimits planLimits(CraftingDispatchLimits limits) {
        return limits;
    }
}
