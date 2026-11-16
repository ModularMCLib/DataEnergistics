package com.fish_dan_.data_energistics.api.registry.provider;

import com.fish_dan_.data_energistics.api.registry.provider.definition.PatternProviderRegistration;
import com.fish_dan_.data_energistics.api.registry.provider.definition.PatternProviderRuntimeRegistration;
import com.fish_dan_.data_energistics.api.registry.provider.definition.PatternProviderWorkstationSourceRegistration;
import com.fish_dan_.data_energistics.api.registry.provider.menu.PatternProviderMenuRegistration;

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

    /**
     * Registers callbacks for native provider types during the plugin's common-setup transaction.
     * Registration IDs must be unique; the handle closes when the plugin returns.
     * Callbacks may inspect live server state only when invoked by the runtime.
     *
     * @param registration type matcher and optional runtime callbacks
     * @throws IllegalStateException when the ID is already staged or the transaction is closed
     */
    void registerRuntime(PatternProviderRuntimeRegistration<?> registration);

    /**
     * Registers one native menu extension during the common plugin transaction. Both logical sides use the same
     * factory after native construction. The native menu itself need not implement a DE control interface.
     *
     * @param registration non-null native menu class and factory; one declaration is allowed per class
     * @throws IllegalStateException when the class is already registered or the transaction is closed
     */
    void registerMenu(PatternProviderMenuRegistration<?> registration);

    /** Registers custom workstation topology for one provider identity family. */
    void registerWorkstationSource(PatternProviderWorkstationSourceRegistration registration);
}
