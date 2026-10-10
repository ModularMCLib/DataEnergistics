package com.fish_dan_.data_energistics.common.entrypoint;

import com.fish_dan_.data_energistics.api.registry.production.DataProductionResourceRegistration;

import appeng.api.stacks.AEKey;

import net.minecraft.resources.ResourceLocation;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import org.jspecify.annotations.Nullable;

/** Immutable lookup table published with the common registry snapshot. */
public final class DataProductionResourceCatalog {

    private final Object2ObjectMap<ResourceLocation, AEKey> keys;
    private final ObjectList<DataProductionResourceRegistration> registrations;

    DataProductionResourceCatalog(Iterable<DataProductionResourceRegistration> source) {
        Object2ObjectLinkedOpenHashMap<ResourceLocation, AEKey> values = new Object2ObjectLinkedOpenHashMap<>();
        ObjectArrayList<DataProductionResourceRegistration> ordered = new ObjectArrayList<>();
        for (DataProductionResourceRegistration registration : source) {
            if (values.putIfAbsent(registration.id(), registration.key()) != null) {
                throw new IllegalStateException("Duplicate data-production resource " + registration.id());
            }
            ordered.add(registration);
        }
        this.keys = values;
        this.registrations = ObjectLists.unmodifiable(ordered);
    }

    /** Looks up a registered key, returning {@code null} for unavailable optional resources. */
    public @Nullable AEKey find(ResourceLocation id) {
        return this.keys.get(id);
    }

    /** Returns the immutable declaration order used for diagnostics and deterministic rule resolution. */
    public ObjectList<DataProductionResourceRegistration> registrations() {
        return this.registrations;
    }

    /** Returns whether a resource identifier is currently available. */
    public boolean contains(ResourceLocation id) {
        return this.keys.containsKey(id);
    }
}
