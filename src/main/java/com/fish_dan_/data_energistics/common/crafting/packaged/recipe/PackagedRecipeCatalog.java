package com.fish_dan_.data_energistics.common.crafting.packaged.recipe;

import com.fish_dan_.data_energistics.api.crafting.packaged.PackagedMachineAdapter;
import com.fish_dan_.data_energistics.common.entrypoint.DataEnergisticsEntrypointLoader;

import net.minecraft.resources.ResourceLocation;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMaps;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import org.jspecify.annotations.Nullable;

/** Frozen machine-family and viewer-category indexes. Contains no level, recipe or block-entity cache. */
public final class PackagedRecipeCatalog {

    private final Object2ObjectMap<ResourceLocation, PackagedMachineAdapter> adapters;
    private final ObjectList<PackagedMachineAdapter> adapterList;
    private final Object2ObjectMap<ResourceLocation, ObjectList<PackagedMachineAdapter>> byType;

    public PackagedRecipeCatalog(ObjectList<PackagedMachineAdapter> declarations) {
        var indexed = new Object2ObjectLinkedOpenHashMap<ResourceLocation, PackagedMachineAdapter>();
        var types = new Object2ObjectLinkedOpenHashMap<ResourceLocation, ObjectList<PackagedMachineAdapter>>();
        for (var adapter : declarations) {
            if (indexed.putIfAbsent(adapter.id(), adapter) != null) {
                throw new IllegalArgumentException("Duplicate packaged machine adapter " + adapter.id());
            }
            for (var type : adapter.recipeTypes()) {
                types.computeIfAbsent(type, ignored -> new ObjectArrayList<>()).add(adapter);
            }
        }
        types.replaceAll((type, values) -> ObjectLists.unmodifiable(values));
        this.adapters = Object2ObjectMaps.unmodifiable(indexed);
        this.adapterList = ObjectLists.unmodifiable(new ObjectArrayList<>(indexed.values()));
        this.byType = Object2ObjectMaps.unmodifiable(types);
    }

    /** Whether the current frozen integrations require exact metadata for this viewer category. */
    public static boolean supportsType(ResourceLocation type) {
        return !DataEnergisticsEntrypointLoader.snapshot().packagedCrafting().forType(type).isEmpty();
    }

    public ObjectList<PackagedMachineAdapter> forType(ResourceLocation type) {
        return this.byType.getOrDefault(type, ObjectList.of());
    }

    public @Nullable PackagedMachineAdapter adapter(ResourceLocation id) {
        return this.adapters.get(id);
    }

    public ObjectList<PackagedMachineAdapter> adapters() {
        return this.adapterList;
    }
}
