package com.fish_dan_.data_energistics.api.registry.provider.runtime;

import net.minecraft.resources.ResourceLocation;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectLists;

import java.util.List;

/**
 * Public location-independent identity supplied by an external pattern-provider integration.
 *
 * <p>
 * The type and schema version identify the provider family. Canonical fields identify one live provider within
 * that family and must remain ordered and deterministic across server restarts.
 * </p>
 *
 * @param type            stable external provider family identifier
 * @param schemaVersion   positive version of the canonical field schema
 * @param canonicalFields ordered deterministic identity fields for one live provider
 */
public record ExternalPatternProviderIdentity(
                                              ResourceLocation type,
                                              int schemaVersion,
                                              List<String> canonicalFields) {

    /**
     * @deprecated scheduled for removal in plan 340; use {@link #canonicalFieldsFast()}
     */
    @Deprecated(forRemoval = true)
    @Override
    public List<String> canonicalFields() {
        return canonicalFields;
    }

    /** Returns an immutable FastUtil view of canonical identity fields. */
    public ObjectList<String> canonicalFieldsFast() {
        return ObjectLists.unmodifiable(new ObjectArrayList<>(canonicalFields));
    }

    /**
     * Validates the schema version and freezes the canonical field list.
     */
    public ExternalPatternProviderIdentity {
        if (schemaVersion <= 0) {
            throw new IllegalArgumentException("External provider identity schema version must be positive");
        }
        canonicalFields = List.copyOf(canonicalFields);
    }
}
