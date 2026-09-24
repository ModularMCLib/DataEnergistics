package com.fish_dan_.data_energistics.common.crafting.packaged.recipe;

import com.fish_dan_.data_energistics.api.crafting.packaged.PackagedMachineAdapter;
import com.fish_dan_.data_energistics.api.registry.provider.runtime.PatternProviderMatchingMetadata;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.jspecify.annotations.Nullable;

/** Resolves the upload declarations exposed by packaged machines adjacent to one live provider. */
public final class PackagedProviderMatchingMetadataResolver {

    private PackagedProviderMatchingMetadataResolver() {}

    /**
     * Inspects only loaded target positions and returns declarations from adapters that recognize those positions.
     *
     * @param level            provider level
     * @param catalog          frozen integration declarations
     * @param providerPosition provider position
     * @param targetSides      adjacent sides currently reachable by the provider
     * @param recipeCategoryId current viewer category, or {@code null} when no category is selected
     * @return immutable declarations for the recognized adjacent machines
     */
    public static PatternProviderMatchingMetadata resolve(ServerLevel level,
                                                          PackagedRecipeCatalog catalog,
                                                          BlockPos providerPosition,
                                                          ObjectList<Direction> targetSides,
                                                          @Nullable ResourceLocation recipeCategoryId) {
        ObjectSet<Direction> visitedSides = new ObjectLinkedOpenHashSet<>();
        ObjectSet<ResourceLocation> recipeCategoryIds = new ObjectLinkedOpenHashSet<>();
        ObjectSet<ResourceLocation> workstationItemIds = new ObjectLinkedOpenHashSet<>();
        for (Direction side : targetSides) {
            if (!visitedSides.add(side)) {
                continue;
            }
            BlockPos machinePosition = providerPosition.relative(side);
            if (!level.isLoaded(machinePosition)) {
                continue;
            }
            for (PackagedMachineAdapter adapter : catalog.adapters()) {
                if ((recipeCategoryId != null && !adapter.recipeTypes().contains(recipeCategoryId)) ||
                        !adapter.recognizes(level, machinePosition)) {
                    continue;
                }
                recipeCategoryIds.addAll(adapter.recipeTypes());
                workstationItemIds.addAll(adapter.workstationItemIds());
            }
        }
        return new PatternProviderMatchingMetadata(
                new ObjectArrayList<>(recipeCategoryIds),
                new ObjectArrayList<>(workstationItemIds));
    }
}
