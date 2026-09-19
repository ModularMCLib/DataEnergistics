package com.fish_dan_.data_energistics.common.crafting.packaged.recipe;

import com.fish_dan_.data_energistics.common.entrypoint.DataEnergisticsEntrypointLoader;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

import org.jspecify.annotations.Nullable;

/** Completes machine-specific, countable recipe inputs before the terminal consumes a blank pattern. */
public final class PackagedPatternEncoding {

    private PackagedPatternEncoding() {}

    public static @Nullable ItemStack complete(ServerLevel level, @Nullable ResourceLocation type,
                                               @Nullable ResourceLocation recipeId, ItemStack encodedPattern) {
        if (type == null) return encodedPattern;
        var adapters = DataEnergisticsEntrypointLoader.snapshot().packagedCrafting().forType(type);
        if (adapters.isEmpty()) return encodedPattern;
        if (recipeId == null) return null;
        ItemStack completed = encodedPattern;
        for (var adapter : adapters) {
            completed = adapter.completeEncoding(level, recipeId, completed);
            if (completed == null) return null;
        }
        return completed;
    }
}
