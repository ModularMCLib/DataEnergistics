package com.fish_dan_.data_energistics.api.entrypoint.client;

import com.fish_dan_.data_energistics.api.registry.adaptive.client.AdaptivePatternProviderToolbarRegistry;
import com.fish_dan_.data_energistics.api.registry.provider.client.PatternProviderScreenRegistry;

/**
 * Client-only registration surfaces, available only for the duration of one plugin callback.
 */
public interface DataEnergisticsClientRegistry {

    /**
     * Returns the non-null Adaptive toolbar factory registry for this transaction.
     */
    AdaptivePatternProviderToolbarRegistry adaptivePatternProviderToolbar();

    /** Returns the non-null native provider screen registry for this client-plugin transaction. */
    PatternProviderScreenRegistry patternProviderScreens();
}
