package com.fish_dan_.data_energistics.api.registry.adaptive;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.KeyCounter;

import org.jspecify.annotations.Nullable;

/**
 * Operations exposed to an adaptive provider dispatch registration.
 */
public interface AdaptivePatternProviderDispatchTarget {

    /** Uses AE2's normal pattern-provider route, or {@code null} when unused. */
    @Nullable
    Boolean pushDefault(IPatternDetails patternDetails, KeyCounter[] inputHolder);

    /** Dispatches an Advanced AE directional pattern, or {@code null} when unused. */
    @Nullable
    Boolean pushAdvancedDirectional(IPatternDetails patternDetails, KeyCounter[] inputHolder);

    /** Dispatches a mechanical Applied Create pattern, or {@code null} when unused. */
    @Nullable
    Boolean pushMechanical(IPatternDetails patternDetails, KeyCounter[] inputHolder);

    /** Dispatches a Meteorite molecular-assembler pattern, or {@code null} when unused. */
    @Nullable
    Boolean pushMeteorite(IPatternDetails patternDetails, KeyCounter[] inputHolder);

    /** Dispatches an AE2CS resonating pattern, or {@code null} when unused. */
    @Nullable
    Boolean pushResonating(IPatternDetails patternDetails, KeyCounter[] inputHolder);
}
