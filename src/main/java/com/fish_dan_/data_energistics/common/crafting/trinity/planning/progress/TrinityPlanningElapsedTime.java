package com.fish_dan_.data_energistics.common.crafting.trinity.planning.progress;

/** Wall-clock duration of one confirmation request, including server capture and asynchronous solving. */
public final class TrinityPlanningElapsedTime {

    private long started;
    private long elapsed;
    private boolean running;

    /** Starts or replaces a server-thread-confined request using a {@link System#nanoTime()} reading. */
    public void start(long started) {
        this.started = started;
        this.elapsed = 0L;
        this.running = true;
    }

    /** Restores a completed request when its plan transfers back from another menu. */
    public void restore(long elapsed) {
        this.elapsed = elapsed;
        this.running = false;
    }

    /** Samples every menu tick independently of progress publications; the first completed sample freezes it. */
    public long sample(long now, boolean complete) {
        if (this.running) {
            this.elapsed = now - this.started;
            this.running = !complete;
        }
        return this.elapsed;
    }
}
