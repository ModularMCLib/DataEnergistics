package com.fish_dan_.data_energistics.api.registry.adaptive;

import appeng.api.crafting.IPatternDetails;

import org.jspecify.annotations.Nullable;

/**
 * Dispatch extension for one adaptive pattern-provider registration.
 *
 * <p>The callback is evaluated before the normal AE2 provider route. Returning
 * {@code null} leaves the pattern to the next route; returning a boolean claims
 * the pattern and supplies its result. This keeps provider-specific dispatch
 * out of the adaptive provider's central type checks.</p>
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
     * Attempts to dispatch one pattern.
     *
     * @param context immutable request and registered target operations
     * @return {@code null} when this route does not claim the pattern
     */
    @Nullable
    Boolean dispatch(AdaptivePatternProviderDispatchContext context);
}
