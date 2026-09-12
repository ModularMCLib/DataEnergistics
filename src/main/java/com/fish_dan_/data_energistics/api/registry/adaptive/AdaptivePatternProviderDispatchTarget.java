package com.fish_dan_.data_energistics.api.registry.adaptive;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.KeyCounter;

/**
 * Operations exposed to an adaptive provider dispatch registration.
 */
public interface AdaptivePatternProviderDispatchTarget {

    /** Uses AE2's normal pattern-provider route. */
    boolean pushDefault(IPatternDetails patternDetails, KeyCounter[] inputHolder);

    /** Checks whether the pattern is an Advanced AE directional pattern. */
    boolean supportsAdvancedDirectional(IPatternDetails patternDetails);

    /** Dispatches an Advanced AE directional pattern. */
    boolean pushAdvancedDirectional(IPatternDetails patternDetails, KeyCounter[] inputHolder);

    /** Dispatches a mechanical Applied Create pattern. */
    boolean pushMechanical(IPatternDetails patternDetails, KeyCounter[] inputHolder);

    /** Checks whether the pattern supports the Meteorite route. */
    boolean supportsMeteorite(IPatternDetails patternDetails);

    /** Dispatches a Meteorite molecular-assembler pattern. */
    boolean pushMeteorite(IPatternDetails patternDetails, KeyCounter[] inputHolder);

    /** Checks whether the pattern supports the AE2CS resonating route. */
    boolean supportsResonating(IPatternDetails patternDetails);

    /** Dispatches an AE2CS resonating pattern. */
    boolean pushResonating(IPatternDetails patternDetails, KeyCounter[] inputHolder);
}
