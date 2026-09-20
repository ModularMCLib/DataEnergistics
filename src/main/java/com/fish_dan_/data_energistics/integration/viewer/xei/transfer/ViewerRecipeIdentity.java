package com.fish_dan_.data_energistics.integration.viewer.xei.transfer;

import com.fish_dan_.data_energistics.integration.viewer.xei.recipe.DataChargePressRecipeView;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;

import org.jspecify.annotations.Nullable;

/** Resolves the exact native recipe behind a viewer object, without guessing from its displayed ingredients. */
public final class ViewerRecipeIdentity {

    private ViewerRecipeIdentity() {}

    public static @Nullable ResourceLocation resolve(Object recipe, Iterable<RecipeHolder<?>> recipes) {
        if (recipe instanceof DataChargePressRecipeView view) return view.patternRecipeId();
        if (recipe instanceof RecipeHolder<?> holder) return holder.id();
        if (recipe instanceof Recipe<?>) {
            for (var holder : recipes) if (holder.value() == recipe) return holder.id();
        }
        return null;
    }
}
