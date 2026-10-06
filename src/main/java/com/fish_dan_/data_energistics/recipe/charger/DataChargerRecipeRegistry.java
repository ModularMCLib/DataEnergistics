package com.fish_dan_.data_energistics.recipe.charger;

import com.fish_dan_.data_energistics.recipe.chargepress.DataChargePressRecipe;
import com.fish_dan_.data_energistics.registry.DERecipes;

import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLists;

import java.util.Comparator;
import java.util.List;

/**
 * Owns the recipe collections used by Data Energistics charger machines.
 *
 * <p>
 * Minecraft still loads these recipes through its data-pack pipeline, but machine code and viewers consume these
 * deterministic snapshots instead of independently asking the vanilla manager for a type and then applying a
 * different matching order.
 * </p>
 */
public final class DataChargerRecipeRegistry {

    private static final Comparator<RecipeHolder<?>> RECIPE_ID_ORDER = Comparator.comparing(
            holder -> holder.id().toString());

    private DataChargerRecipeRegistry() {}

    /** Returns custom data charger recipes in the order used by the machine and viewers. */
    public static List<RecipeHolder<DataChargerRecipe>> dataChargerRecipes(RecipeManager recipeManager) {
        ObjectArrayList<RecipeHolder<DataChargerRecipe>> recipes = new ObjectArrayList<>(
                recipeManager.getAllRecipesFor(DERecipes.DATA_CHARGER_TYPE.get()));
        recipes.sort(RECIPE_ID_ORDER);
        return ObjectLists.unmodifiable(recipes);
    }

    /** Returns custom integrated-charger recipes in the order used by the machine and viewers. */
    public static List<RecipeHolder<DataIntegratedChargerRecipe>> integratedChargerRecipes(
                                                                                           RecipeManager recipeManager) {
        ObjectArrayList<RecipeHolder<DataIntegratedChargerRecipe>> recipes = new ObjectArrayList<>(
                recipeManager.getAllRecipesFor(DERecipes.DATA_INTEGRATED_CHARGER_TYPE.get()));
        recipes.sort(RECIPE_ID_ORDER);
        return ObjectLists.unmodifiable(recipes);
    }

    /** Returns integrated-charger recipes with the most complete input signatures checked first. */
    public static List<RecipeHolder<DataIntegratedChargerRecipe>> integratedChargerMatchingOrder(
                                                                                                 RecipeManager recipeManager) {
        ObjectArrayList<RecipeHolder<DataIntegratedChargerRecipe>> recipes = new ObjectArrayList<>(
                recipeManager.getAllRecipesFor(DERecipes.DATA_INTEGRATED_CHARGER_TYPE.get()));
        recipes.sort(Comparator.<RecipeHolder<DataIntegratedChargerRecipe>>comparingInt(
                holder -> holder.value().inputs().size()).reversed().thenComparing(RECIPE_ID_ORDER));
        return ObjectLists.unmodifiable(recipes);
    }

    /** Returns custom crystal-growth recipes in the order used by the machine and viewers. */
    public static List<RecipeHolder<DataChargePressRecipe>> chargePressRecipes(RecipeManager recipeManager) {
        ObjectArrayList<RecipeHolder<DataChargePressRecipe>> recipes = new ObjectArrayList<>(
                recipeManager.getAllRecipesFor(DERecipes.DATA_CHARGE_PRESS_TYPE.get()));
        recipes.sort(RECIPE_ID_ORDER);
        return ObjectLists.unmodifiable(recipes);
    }

    /** Returns crystal-growth recipes with the most complete input signatures checked first. */
    public static List<RecipeHolder<DataChargePressRecipe>> chargePressMatchingOrder(RecipeManager recipeManager) {
        ObjectArrayList<RecipeHolder<DataChargePressRecipe>> recipes = new ObjectArrayList<>(
                recipeManager.getAllRecipesFor(DERecipes.DATA_CHARGE_PRESS_TYPE.get()));
        recipes.sort(Comparator.<RecipeHolder<DataChargePressRecipe>>comparingInt(
                holder -> holder.value().inputs().size()).reversed().thenComparing(RECIPE_ID_ORDER));
        return ObjectLists.unmodifiable(recipes);
    }
}
