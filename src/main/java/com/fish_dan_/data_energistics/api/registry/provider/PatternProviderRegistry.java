package com.fish_dan_.data_energistics.api.registry.provider;

import com.fish_dan_.data_energistics.api.registry.provider.definition.PatternProviderRegistration;
import com.fish_dan_.data_energistics.api.registry.provider.definition.PatternProviderWorkstationSourceRegistration;

/**
 * Declaration facet for provider metadata and provider-lifecycle integrations.
 */
public interface PatternProviderRegistry {

    /**
     * Registers one atomic provider declaration.
     *
     * @param registration metadata and lifecycle callbacks to stage
     */
    void register(PatternProviderRegistration registration);

    /** Registers custom workstation topology for one provider identity family. */
    void registerWorkstationSource(PatternProviderWorkstationSourceRegistration registration);
}
