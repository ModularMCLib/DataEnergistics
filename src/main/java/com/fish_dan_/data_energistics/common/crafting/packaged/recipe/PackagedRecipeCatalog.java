package com.fish_dan_.data_energistics.common.crafting.packaged.recipe;

import com.fish_dan_.data_energistics.api.crafting.packaged.PackagedMachineAdapter;
import com.fish_dan_.data_energistics.common.entrypoint.DataEnergisticsEntrypointLoader;

import net.minecraft.resources.ResourceLocation;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMaps;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;

/** Frozen machine-family and viewer-category indexes. Contains no level, recipe or block-entity cache. */
public final class PackagedRecipeCatalog {

    private final Object2ObjectMap<ResourceLocation, PackagedMachineAdapter> adapters;
    private final ObjectList<PackagedMachineAdapter> adapterList;
    private final Object2ObjectMap<ResourceLocation, ObjectList<PackagedMachineAdapter>> byType;
    private final ObjectList<ResourceLocation> recipeCategoryIds;
    private final ObjectList<ResourceLocation> workstationItemIds;

    public PackagedRecipeCatalog(ObjectList<PackagedMachineAdapter> declarations) {
        var indexed = new Object2ObjectLinkedOpenHashMap<ResourceLocation, PackagedMachineAdapter>();
        var types = new Object2ObjectLinkedOpenHashMap<ResourceLocation, ObjectList<PackagedMachineAdapter>>();
        var recipeCategoryIds = new ObjectLinkedOpenHashSet<ResourceLocation>();
        var workstationItemIds = new ObjectLinkedOpenHashSet<ResourceLocation>();
        for (var adapter : declarations) {
            if (indexed.putIfAbsent(adapter.id(), adapter) != null) {
                throw new IllegalArgumentException("Duplicate packaged machine adapter " + adapter.id());
            }
            for (var type : adapter.recipeTypes()) {
                types.computeIfAbsent(type, ignored -> new ObjectArrayList<>()).add(adapter);
            }
            recipeCategoryIds.addAll(adapter.recipeTypes());
            workstationItemIds.addAll(adapter.workstationItemIds());
        }
        types.replaceAll((type, values) -> ObjectLists.unmodifiable(values));
        this.adapters = Object2ObjectMaps.unmodifiable(indexed);
        this.adapterList = ObjectLists.unmodifiable(new ObjectArrayList<>(indexed.values()));
        this.byType = Object2ObjectMaps.unmodifiable(types);
        this.recipeCategoryIds = canonicalIds(recipeCategoryIds);
        this.workstationItemIds = canonicalIds(workstationItemIds);
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

    /** Returns all recipe categories declared by the frozen packaged-machine integrations. */
    public ObjectList<ResourceLocation> recipeCategoryIds() {
        return this.recipeCategoryIds;
    }

    /** Returns all workstation item IDs declared by the frozen packaged-machine integrations. */
    public ObjectList<ResourceLocation> workstationItemIds() {
        return this.workstationItemIds;
    }

    private static ObjectList<ResourceLocation> canonicalIds(ObjectLinkedOpenHashSet<ResourceLocation> ids) {
        var canonical = new ObjectArrayList<>(ids);
        canonical.sort(Comparator.comparing(ResourceLocation::toString));
        return ObjectLists.unmodifiable(canonical);
    }
}
