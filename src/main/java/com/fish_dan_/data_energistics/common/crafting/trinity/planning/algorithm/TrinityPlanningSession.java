package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.progress.TrinityPlanningProgressPhase;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.progress.TrinityPlanningProgressReporter;

import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/**
 * Request-local owner of the monotonic planning budget and cooperative cancellation source.
 * <p>
 * Structural compilation, route search and diagnostic work share one deadline. No retry replenishes the budget.
 * Server-thread input capture happens before this session. This object is thread-confined and is never cached.
 */
public final class TrinityPlanningSession {

    /**
     * Creates one request session whose budget starts immediately.
     */
    public static TrinityPlanningSession create(
                                                BooleanSupplier cancellation,
                                                LongSupplier nanoClock,
                                                long planningBudgetNanos,
                                                TrinityPlanningProgressReporter progress) {
        if (planningBudgetNanos <= 0L) {
            throw new IllegalArgumentException("A Trinity planning session requires cancellation, a clock and a positive budget");
        }
        return new TrinityPlanningSession(cancellation, nanoClock, planningBudgetNanos, progress);
    }

    private final TrinityPlanningMetrics metrics;
    private final TrinityPlanningControl control;

    private TrinityPlanningSession(
                                   BooleanSupplier cancellation,
                                   LongSupplier nanoClock,
                                   long planningBudgetNanos,
                                   TrinityPlanningProgressReporter progress) {
        this.metrics = TrinityPlanningMetrics.create(progress);
        this.control = TrinityPlanningControl.create(cancellation, nanoClock, planningBudgetNanos, this.metrics);
    }

    /** Starts one solver phase whose counters are published through this request's detached reporter. */
    public void beginSolving(TrinityPlanningProgressPhase phase, int routeStateLimit) {
        this.metrics.beginPhase(phase, routeStateLimit);
    }

    /**
     * @return shared bounded control used for compilation and every first-feasible attempt
     */
    public TrinityPlanningControl feasibilityControl() {
        return this.control;
    }

    /**
     * Returns the same bounded control while time remains. Empty means the request must stop, not start a fresh pass.
     */
    public Optional<TrinityPlanningControl> boundedControl() {
        long remaining = remainingPlanningNanos();
        return remaining == 0L ? Optional.empty() : Optional.of(this.control);
    }

    /** @return time spent in actual ojAlgo passes across the complete request */
    public long mipNanos() {
        return this.metrics.mipNanos();
    }

    /** @return actual ojAlgo minimise, maximise and probe passes across the complete request */
    public int solverPasses() {
        return this.metrics.solverPasses();
    }

    /** @return base or encoded solver models assembled across the complete request */
    public int solverModels() {
        return this.metrics.solverModels();
    }

    /** @return joint branch-and-bound states charged across the complete request */
    public int jointStates() {
        return this.metrics.jointStates();
    }

    /** @return DAG or mixed-graph route states charged across the complete request */
    public int routeStates() {
        return this.metrics.routeStates();
    }

    /**
     * @return remaining non-negative planning budget
     */
    public long remainingPlanningNanos() {
        return this.control.remainingNanos();
    }
}
