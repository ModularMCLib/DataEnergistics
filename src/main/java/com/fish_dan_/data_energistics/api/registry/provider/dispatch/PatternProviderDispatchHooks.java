package com.fish_dan_.data_energistics.api.registry.provider.dispatch;

import com.fish_dan_.data_energistics.common.pattern.provider.redstone.RedstoneTuningDispatch;

import org.jspecify.annotations.NullMarked;

/**
 * Provider-local execution policy, supplied by a registration or an optional native compatibility wrapper. The native
 * logic class need not implement this interface. Invoke only on the owning server thread after construction; factory
 * callbacks may capture native objects but must defer reading mutable state until these lifecycle calls.
 */
@NullMarked
public interface PatternProviderDispatchHooks {

    /** Records one successful physical or counted push. Failed or simulated pushes must not call this method. */
    void patternPushed();

    /** Samples or schedules an input change before native lock-state processing. */
    void redstoneInputChanged();

    /** Updates tuning state and consumes a queued unlock pulse immediately before the native work loop. */
    void beforeWork();

    /** Completes a pending dispatch after the native work loop has transferred its input remainder. */
    void afterWork();

    /** Attempts one pulse-mode output request; returns false when the mode or native level does not accept it. */
    boolean forcePulseUnlock();

    /** Creates the shared redstone dispatch policy from non-null live callbacks, without reading native state yet. */
    static PatternProviderDispatchHooks redstoneTuning(RedstoneTuningDispatchContext context) {
        return new RedstoneTuningDispatch(context, false);
    }

    /** Creates the resonating/mechanical policy, which also consumes a queued request after dispatch completes. */
    static PatternProviderDispatchHooks requestingRedstoneTuning(RedstoneTuningDispatchContext context) {
        return new RedstoneTuningDispatch(context, true);
    }
}
