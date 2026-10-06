package com.fish_dan_.data_energistics.integration.viewer.xei.transfer;

import com.fish_dan_.data_energistics.integration.magic.forbiddenarcanus.viewer.HephaestusRitualIdentity;
import com.fish_dan_.data_energistics.integration.viewer.xei.recipe.DataChargePressRecipeView;
import com.fish_dan_.data_energistics.integration.viewer.xei.recipe.DataRipperReassemblerRecipeView;

import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.fml.ModList;

import org.jspecify.annotations.Nullable;

/** Resolves the exact native recipe behind a viewer object, without guessing from its displayed ingredients. */
public final class ViewerRecipeIdentity {

    private ViewerRecipeIdentity() {}

    public static @Nullable ResourceLocation resolve(Object recipe, Iterable<RecipeHolder<?>> recipes,
                                                     @Nullable RegistryAccess registries) {
        switch (recipe) {
            case DataChargePressRecipeView view -> {
                return view.patternRecipeId();
            }
            case DataRipperReassemblerRecipeView view -> {
                return view.patternRecipeId();
            }
            case RecipeHolder<?> holder -> {
                return holder.id();
            }
            case Recipe<?> ignored -> {
                for (var holder : recipes) if (holder.value() == recipe) return holder.id();
            }
            default -> {}
        }
        if (registries != null && ModList.get().isLoaded("forbidden_arcanus")) {
            return HephaestusRitualIdentity.resolve(recipe, registries);
        }
        return null;
    }
}
