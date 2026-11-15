package com.fish_dan_.data_energistics.api.registry.provider.runtime;

import appeng.helpers.patternprovider.PatternContainer;

/**
 * Registered callback linking an AE2 publication to its terminal-visible container.
 * The publication class does not implement this interface. Called on the server
 * thread during mount; the container must share the publication's lifecycle.
 *
 * @param <T> original publication type selected by the registration
 */
@FunctionalInterface
public interface PatternProviderRuntimeLink<T> {

    /**
     * Returns the terminal-visible container represented by this publication.
     *
     * @param provider live publication selected by the runtime registration
     * @return provider host used for stable identity resolution and terminal actions
     */
    PatternContainer patternContainer(T provider);
}
