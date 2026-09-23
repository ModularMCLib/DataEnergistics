package com.fish_dan_.data_energistics.api.registry.provider.runtime;

import net.minecraft.resources.ResourceLocation;

import it.unimi.dsi.fastutil.objects.ObjectList;

/**
 * Supplies immutable recipe-category and workstation declarations for a terminal-visible provider.
 *
 * <p>
 * This source is for providers whose matching declarations are derived from their runtime implementation rather
 * than from a registered physical-provider identity. It is read while the upload terminal builds its provider
 * ranking; it must not perform world access, mutate provider state, or retain a mutable result.
 * </p>
 */
public interface PatternProviderMatchingMetadataSource {

    /**
     * Returns the recipe-viewer category IDs accepted by this provider.
     *
     * @return immutable recipe-category IDs using the same registration IDs as the viewer adapter
     */
    ObjectList<ResourceLocation> recipeCategoryIds();

    /**
     * Returns the workstation item IDs represented by this provider.
     *
     * @return immutable item registration IDs for the matching workstations
     */
    ObjectList<ResourceLocation> workstationItemIds();
}
