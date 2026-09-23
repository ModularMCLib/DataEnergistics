package com.fish_dan_.data_energistics.api.registry.provider.runtime;

import net.minecraft.resources.ResourceLocation;

import org.jspecify.annotations.Nullable;

/**
 * Supplies immutable recipe-category and workstation declarations for a terminal-visible provider.
 *
 * <p>
 * This source is for providers whose matching declarations are derived from their runtime targets rather than from a
 * registered physical-provider identity. It is read while the upload terminal builds its provider ranking; it may
 * inspect already loaded state but must not load chunks, mutate provider state, or retain a mutable result.
 * </p>
 */
public interface PatternProviderMatchingMetadataSource {

    /**
     * Resolves the declarations for the provider's current runtime targets.
     *
     * @param recipeCategoryId current viewer category, or {@code null} when no category is selected
     * @return immutable declarations; an empty value means that no current target is recognized
     */
    PatternProviderMatchingMetadata matchingMetadata(@Nullable ResourceLocation recipeCategoryId);
}
