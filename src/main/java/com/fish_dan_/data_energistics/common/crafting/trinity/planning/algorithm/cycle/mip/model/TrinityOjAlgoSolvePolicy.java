package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.model;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningControl;

import org.ojalgo.optimisation.ExpressionsBasedModel;

import java.util.concurrent.TimeUnit;

/** Applies the remaining request budget to ojAlgo, with a finite limit for cancellation-only callers. */
public final class TrinityOjAlgoSolvePolicy {

    private static final long MAX_CALL_MILLIS = 5_000L;

    private TrinityOjAlgoSolvePolicy() {}

    /**
     * Applies one request-aware limit to both integer search and its linear subproblems. Feasibility passes use
     * a zero objective; a short time_suffice would also truncate linear subproblems before they can certify a node.
     */
    public static void configure(
                                 ExpressionsBasedModel model,
                                 TrinityPlanningControl control) {
        long abortMillis = MAX_CALL_MILLIS;
        if (control.deadlineConfigured()) {
            long remainingNanos = control.remainingNanos();
            long remainingMillis = TimeUnit.NANOSECONDS.toMillis(remainingNanos) +
                    (remainingNanos % 1_000_000L == 0L ? 0L : 1L);
            abortMillis = Math.max(1L, remainingMillis);
        }
        model.options.time_abort = abortMillis;
        model.options.time_suffice = abortMillis;
    }
}
