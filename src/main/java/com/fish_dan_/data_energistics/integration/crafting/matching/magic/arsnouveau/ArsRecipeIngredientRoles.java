package com.fish_dan_.data_energistics.integration.crafting.matching.magic.arsnouveau;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.crafting.matching.RecipeMatchingRuleAdapter;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;

import com.hollingsworth.arsnouveau.common.crafting.recipes.ImbuementRecipe;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Optional Ars role expansion keeps both consumed central material and retained pedestal constraints.
 */
@NullMarked
public final class ArsRecipeIngredientRoles implements RecipeMatchingRuleAdapter {

    @Override
    public ResourceLocation id() {
        return Data_Energistics.id("ars_nouveau_recipe_matching");
    }

    @Override
    public @Nullable ObjectList<Ingredient> inputIngredients(ServerLevel level, ResourceLocation recipeId) {
        var holder = level.getRecipeManager().byKey(recipeId);
        return holder.isPresent() ? resolve(holder.get().value()) : null;
    }

    @Override
    public @Nullable ObjectList<Ingredient> outputIngredients(ServerLevel level, ResourceLocation recipeId) {
        var holder = level.getRecipeManager().byKey(recipeId);
        if (holder.isEmpty() || !(holder.get().value() instanceof ImbuementRecipe recipe)) return null;
        var outputs = new ObjectArrayList<>(recipe.getPedestalItems());
        outputs.add(Ingredient.of(recipe.getOutput()));
        return ObjectLists.unmodifiable(outputs);
    }

    private static @Nullable ObjectList<Ingredient> resolve(Recipe<?> recipe) {
        if (!(recipe instanceof ImbuementRecipe imbuement)) return null;
        var roles = new ObjectArrayList<Ingredient>();
        roles.add(imbuement.getInput());
        roles.addAll(imbuement.getPedestalItems());
        return ObjectLists.unmodifiable(roles);
    }
}
