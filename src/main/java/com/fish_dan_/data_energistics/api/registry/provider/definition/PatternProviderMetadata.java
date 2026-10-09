package com.fish_dan_.data_energistics.api.registry.provider.definition;

import net.minecraft.resources.ResourceLocation;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import it.unimi.dsi.fastutil.objects.ObjectSet;

import java.util.Comparator;

/**
 * Immutable semantic metadata used to match a declared provider integration.
 *
 * <p>
 * Category and workstation IDs are canonicalized as sorted, duplicate-free snapshots. Matching code must compare
 * the complete values; no display names, class names or namespace heuristics are implied by this type.
 * </p>
 *
 * @param registrationId     stable identity of the plugin registration
 * @param providerIdentity   stable declaration-time provider identity schema
 * @param recipeCategoryIds  complete recipe-category ID set understood by the provider
 * @param workstationItemIds complete workstation item-ID set understood by the provider
 */
public record PatternProviderMetadata(ResourceLocation registrationId,
                                      ProviderIdentityDescriptor providerIdentity,
                                      ObjectList<ResourceLocation> recipeCategoryIds,
                                      ObjectList<ResourceLocation> workstationItemIds) {

    /** Returns an immutable FastUtil view of recipe category IDs. */
    public ObjectList<ResourceLocation> recipeCategoryIdsFast() {
        return ObjectLists.unmodifiable(new ObjectArrayList<>(recipeCategoryIds));
    }

    /** Returns an immutable FastUtil view of workstation item IDs. */
    public ObjectList<ResourceLocation> workstationItemIdsFast() {
        return ObjectLists.unmodifiable(new ObjectArrayList<>(workstationItemIds));
    }

    /**
     * Validates and freezes provider metadata at the public registration boundary.
     */
    public PatternProviderMetadata {
        recipeCategoryIds = canonicalIds(recipeCategoryIds);
        workstationItemIds = canonicalIds(workstationItemIds);
    }

    private static ObjectList<ResourceLocation> canonicalIds(ObjectList<ResourceLocation> ids) {
        ObjectSet<ResourceLocation> unique = new ObjectLinkedOpenHashSet<>(ids);
        ObjectArrayList<ResourceLocation> canonical = new ObjectArrayList<>(unique);
        canonical.sort(Comparator.comparing(ResourceLocation::toString));
        return new ObjectImmutableList<>(canonical);
    }
}
