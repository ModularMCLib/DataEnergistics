package com.fish_dan_.data_energistics.api.registry.provider.runtime;

/**
 * Registered callback for terminal-visible containers that cannot use a built-in physical identity.
 * The provider class does not implement this interface. Called on the server thread;
 * the result must remain stable across menu sessions and server restarts.
 *
 * @param <T> original container type selected by the registration
 */
@FunctionalInterface
public interface PatternProviderIdentitySource<T> {

    /**
     * Reads the stable identity without changing the provider or loading chunks.
     *
     * @param provider live instance selected by the runtime registration
     * @return versioned external provider identity
     */
    ExternalPatternProviderIdentity providerIdentity(T provider);
}
