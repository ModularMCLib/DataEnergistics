package com.fish_dan_.data_energistics.api.registry.adaptive;

import appeng.api.crafting.IPatternDetails;

/**
 * Dispatch extension for one adaptive pattern-provider registration.
 *
 * <p>
 * The callback is evaluated before the normal AE2 provider route. The
 * separate applicability check keeps the result primitive while still
 * distinguishing an unclaimed pattern from a failed dispatch.
 * </p>
 */
@FunctionalInterface
public interface AdaptivePatternProviderDispatch {

    /**
     * Whether this registration needs the adaptive counted-batch preparation
     * path for the supplied pattern.
     *
     * @param patternDetails pattern being prepared
     * @return whether the special route is required
     */
    default boolean usesSpecialBatchRoute(IPatternDetails patternDetails) {
        return false;
    }

    /**
     * Checks whether this route claims one pattern.
     */
    default boolean handles(AdaptivePatternProviderDispatchContext context) {
        return false;
    }

    /**
     * Attempts to dispatch one claimed pattern.
     */
    boolean dispatch(AdaptivePatternProviderDispatchContext context);
}
