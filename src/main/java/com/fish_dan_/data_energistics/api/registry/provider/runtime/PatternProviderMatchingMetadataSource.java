package com.fish_dan_.data_energistics.api.registry.provider.runtime;

import net.minecraft.resources.ResourceLocation;

import org.jspecify.annotations.Nullable;

/**
 * Registered callback supplying immutable matching declarations for a live provider.
 *
 * <p>
 * The provider class does not implement this interface. Declarations come from the provider's current runtime targets.
 * It is read on the server thread while the upload terminal builds its provider ranking; it may
 * inspect already loaded state but must not load chunks, mutate provider state, or retain a mutable result.
 * </p>
 *
 * @param <T> original provider type selected by the registration
 */
@FunctionalInterface
public interface PatternProviderMatchingMetadataSource<T> {

    /**
     * Resolves the declarations for the provider's current runtime targets.
     *
     * @param provider         live instance selected by the runtime registration
     * @param recipeCategoryId current viewer category, or {@code null} when no category is selected
     * @return immutable declarations; an empty value means that no current target is recognized
     */
    PatternProviderMatchingMetadata matchingMetadata(T provider, @Nullable ResourceLocation recipeCategoryId);
}
