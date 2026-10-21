package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.governor;

import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.budget.CraftingDispatchLimits;

import java.util.function.LongSupplier;

/**
 * Plans one immutable tick quota from the previous complete Minecraft tick and measured physical-call costs.
 */
final class MeasuredCraftingServerDispatchBudget implements CraftingServerDispatchBudget {

    static final CraftingServerDispatchBudget UNBOUNDED = new CraftingServerDispatchBudget() {

        @Override
        public boolean canStart(long activeDispatchNanos) {
            if (activeDispatchNanos < 0L) {
                throw new IllegalArgumentException("Active dispatch duration must not be negative");
            }
            return true;
        }

        @Override
        public void record(long elapsedNanos) {
            if (elapsedNanos < 0L) {
                throw new IllegalArgumentException("Recorded dispatch duration must not be negative");
            }
        }
    };

    private final LongSupplier nanoClock;
    private final long targetTickNanos;
    private final long overloadedTrickleNanos;
    private long dispatchStartedAtNanos;
    private long currentDispatchNanos;
    private long currentCaptureNanos;
    private long lastCompletedDispatchNanos;
    private long previousNonDispatchNanos;
    private long previousAttemptNanos;
    private long previousCaptureAttemptNanos;
    private int physicalAttempts;
    private int plannedAttempts;
    private boolean dispatchPassActive;
    private boolean tickActive;

    MeasuredCraftingServerDispatchBudget(
                                         LongSupplier nanoClock,
                                         long targetTickNanos,
                                         long overloadedTrickleNanos) {
        if (nanoClock == null) {
            throw new IllegalArgumentException("Server dispatch nano clock is required");
        }
        if (overloadedTrickleNanos <= 0L || targetTickNanos < overloadedTrickleNanos) {
            throw new IllegalArgumentException("Server dispatch tick budgets are out of range");
        }
        this.nanoClock = nanoClock;
        this.targetTickNanos = targetTickNanos;
        this.overloadedTrickleNanos = overloadedTrickleNanos;
    }

    @Override
    public boolean canStart(long activeDispatchNanos) {
        if (activeDispatchNanos < 0L) {
            throw new IllegalArgumentException("Active dispatch duration must not be negative");
        }
        if (!this.tickActive) {
            return true;
        }
        return this.physicalAttempts < this.plannedAttempts;
    }

    @Override
    public void record(long elapsedNanos) {
        if (elapsedNanos < 0L) {
            throw new IllegalArgumentException("Recorded dispatch duration must not be negative");
        }
        if (this.tickActive) {
            this.currentDispatchNanos = Math.addExact(this.currentDispatchNanos, elapsedNanos);
        }
    }

    @Override
    public boolean canCompleteFirstAttempt() {
        return this.tickActive && this.physicalAttempts == 0 && this.plannedAttempts > 0;
    }

    @Override
    public void recordPhysicalAttempt() {
        if (this.tickActive) {
            if (this.physicalAttempts >= this.plannedAttempts) {
                throw new IllegalStateException("Server dispatch acquired more physical calls than its planned tick quota");
            }
            this.physicalAttempts = Math.incrementExact(this.physicalAttempts);
        }
    }

    @Override
    public void recordCapacityCapture(long elapsedNanos) {
        record(elapsedNanos);
        if (this.tickActive) {
            this.currentCaptureNanos = Math.addExact(this.currentCaptureNanos, elapsedNanos);
        }
    }

    @Override
    public boolean usesHistoricalQuota() {
        return true;
    }

    @Override
    public CraftingDispatchLimits planLimits(CraftingDispatchLimits limits) {
        int attempts = Math.min(limits.maxAttemptsPerGrid(), estimatedAttempts(limits.maxServerSubmissionNanos(), this.previousAttemptNanos));
        attempts = Math.min(attempts, estimatedAttempts(limits.maxCapacityCaptureNanos(), this.previousCaptureAttemptNanos));
        return new CraftingDispatchLimits(attempts, Math.min(attempts, limits.maxAttemptsPerProvider()),
                limits.maxServerSubmissionNanos(), limits.maxCapacityCaptureNanos());
    }

    void beginTick() {
        long allowance = this.previousNonDispatchNanos >= this.targetTickNanos ? this.overloadedTrickleNanos :
                this.targetTickNanos - this.previousNonDispatchNanos;
        this.plannedAttempts = estimatedAttempts(allowance, this.previousAttemptNanos);
        this.currentDispatchNanos = 0L;
        this.currentCaptureNanos = 0L;
        this.physicalAttempts = 0;
        this.tickActive = true;
    }

    void beginDispatchPass() {
        this.dispatchStartedAtNanos = this.nanoClock.getAsLong();
        this.dispatchPassActive = true;
    }

    void completeDispatchPass() {
        if (!this.dispatchPassActive) {
            throw new IllegalStateException("Server dispatch pass was not started");
        }
        this.currentDispatchNanos = Math.max(this.currentDispatchNanos, elapsed(this.dispatchStartedAtNanos, this.nanoClock.getAsLong()));
        this.dispatchPassActive = false;
    }

    void completeTick(long completeTickNanos) {
        if (completeTickNanos < 0L) {
            throw new IllegalArgumentException("Complete server tick duration must not be negative");
        }
        if (this.dispatchPassActive) {
            throw new IllegalStateException("Server tick completed before its dispatch pass");
        }
        this.lastCompletedDispatchNanos = this.currentDispatchNanos;
        this.previousNonDispatchNanos = Math.max(0L, completeTickNanos - this.currentDispatchNanos);
        if (this.physicalAttempts > 0) {
            this.previousAttemptNanos = Math.max(1L, Math.ceilDiv(this.currentDispatchNanos, this.physicalAttempts));
            this.previousCaptureAttemptNanos = Math.ceilDiv(this.currentCaptureNanos, this.physicalAttempts);
        }
        this.tickActive = false;
    }

    void reset() {
        this.dispatchStartedAtNanos = 0L;
        this.currentDispatchNanos = 0L;
        this.currentCaptureNanos = 0L;
        this.lastCompletedDispatchNanos = 0L;
        this.previousNonDispatchNanos = 0L;
        this.previousAttemptNanos = 0L;
        this.previousCaptureAttemptNanos = 0L;
        this.physicalAttempts = 0;
        this.plannedAttempts = 0;
        this.dispatchPassActive = false;
        this.tickActive = false;
    }

    long lastCompletedDispatchNanos() {
        return this.lastCompletedDispatchNanos;
    }

    private static int estimatedAttempts(long availableNanos, long attemptNanos) {
        // Until the first physical sample exists, configured Grid/provider ceilings bound calibration work.
        return attemptNanos == 0L ? Integer.MAX_VALUE :
                (int) Math.min(Integer.MAX_VALUE, Math.max(1L, availableNanos / attemptNanos));
    }

    private static long elapsed(long startNanos, long endNanos) {
        long elapsed = endNanos - startNanos;
        if (elapsed < 0L) {
            throw new IllegalStateException("Server dispatch nano clock moved backwards");
        }
        return elapsed;
    }
}
