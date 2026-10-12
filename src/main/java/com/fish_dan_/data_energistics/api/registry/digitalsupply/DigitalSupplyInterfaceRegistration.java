package com.fish_dan_.data_energistics.api.registry.digitalsupply;

import net.minecraft.resources.ResourceLocation;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectLists;

/** One atomically registered Digital Supply Interface adapter and its resource catalog. */
public record DigitalSupplyInterfaceRegistration(ResourceLocation id,
                                                 ObjectList<DigitalSupplyResourceDefinition> resources,
                                                 DigitalSupplyInterfaceAdapter adapter) {

    public DigitalSupplyInterfaceRegistration {
        resources = ObjectLists.unmodifiable(new ObjectArrayList<>(resources));
        ObjectLinkedOpenHashSet<ResourceLocation> ids = new ObjectLinkedOpenHashSet<>();
        for (DigitalSupplyResourceDefinition resource : resources) {
            if (!ids.add(resource.id())) {
                throw new IllegalArgumentException("Duplicate digital-supply resource ID " + resource.id());
            }
        }
        if (!id.equals(adapter.id())) {
            throw new IllegalArgumentException(
                    "Digital Supply Interface registration ID " + id + " does not match adapter ID " + adapter.id());
        }
    }
}
