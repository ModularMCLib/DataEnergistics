package com.fish_dan_.data_energistics.api.entrypoint.client;

import com.fish_dan_.data_energistics.api.registry.adaptive.client.AdaptivePatternProviderToolbarRegistry;

/**
 * Client-only registration surfaces, available only for the duration of one plugin callback.
 */
public interface DataEnergisticsClientRegistry {

    /**
     * Returns the non-null Adaptive toolbar factory registry for this transaction.
     */
    AdaptivePatternProviderToolbarRegistry adaptivePatternProviderToolbar();
}
