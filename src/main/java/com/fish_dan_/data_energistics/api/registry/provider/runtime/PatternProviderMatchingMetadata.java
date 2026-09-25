package com.fish_dan_.data_energistics.api.registry.provider.runtime;

import net.minecraft.resources.ResourceLocation;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectLists;

import java.util.Comparator;

/**
 * Immutable upload-search declarations resolved for one live pattern provider instance.
 *
 * @param recipeCategoryIds  recipe-viewer categories currently reachable through this provider
 * @param workstationItemIds workstation item IDs currently reachable through this provider
 */
public record PatternProviderMatchingMetadata(ObjectList<ResourceLocation> recipeCategoryIds,
                                              ObjectList<ResourceLocation> workstationItemIds) {

    public PatternProviderMatchingMetadata {
        recipeCategoryIds = canonicalIds(recipeCategoryIds);
        workstationItemIds = canonicalIds(workstationItemIds);
    }

    /** Returns an empty declaration for a provider with no currently recognized workstation. */
    public static PatternProviderMatchingMetadata empty() {
        return new PatternProviderMatchingMetadata(ObjectList.of(), ObjectList.of());
    }

    private static ObjectList<ResourceLocation> canonicalIds(ObjectList<ResourceLocation> ids) {
        ObjectLinkedOpenHashSet<ResourceLocation> unique = new ObjectLinkedOpenHashSet<>(ids);
        ObjectArrayList<ResourceLocation> canonical = new ObjectArrayList<>(unique);
        canonical.sort(Comparator.comparing(ResourceLocation::toString));
        return ObjectLists.unmodifiable(canonical);
    }
}
