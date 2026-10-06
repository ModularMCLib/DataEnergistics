package com.fish_dan_.data_energistics.integration.crafting;

import com.fish_dan_.data_energistics.integration.MOD;
import com.fish_dan_.data_energistics.integration.ae.advancedae.reassembler.AdvancedAeReassemblerRecipeSource;
import com.fish_dan_.data_energistics.integration.ae.extendedae.reassembler.ExtendedAeReassemblerRecipeSource;
import com.fish_dan_.data_energistics.recipe.reassembler.DataRipperReassemblerRecipe;
import com.fish_dan_.data_energistics.recipe.reassembler.DataRipperReassemblerRecipeInput;
import com.fish_dan_.data_energistics.registry.DERecipes;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
 * Composes native data reassembler recipes with optional integration sources.
 *
 * <p>
 * This class has no cache and no codec. Every call reads the current recipe manager; the individual integration
 * sources own the native recipe classes and conversion rules for their respective mods.
 * </p>
 */
public final class DataRipperReassemblerRecipeSources {

    private DataRipperReassemblerRecipeSources() {}

    /** Returns the complete recipe-viewer set from the current native recipe tables. */
    public static List<RecipeHolder<DataRipperReassemblerRecipe>> all(RecipeManager recipeManager) {
        List<RecipeHolder<DataRipperReassemblerRecipe>> recipes = new ObjectArrayList<>(
                recipeManager.getAllRecipesFor(DERecipes.DATA_RIPPER_REASSEMBLER_TYPE.get()));
        appendAdditional(recipeManager, recipes);
        return List.copyOf(recipes);
    }

    /** Finds one optional integration recipe matching the current machine inputs. */
    public static @Nullable RecipeHolder<DataRipperReassemblerRecipe> find(
                                                                           Level level,
                                                                           DataRipperReassemblerRecipeInput input,
                                                                           Set<ResourceLocation> excludedRecipeIds) {
        RecipeManager recipeManager = level.getRecipeManager();
        if (MOD.isExtendedAeLoaded()) {
            RecipeHolder<DataRipperReassemblerRecipe> recipe = ExtendedAeReassemblerRecipeSource.findRecipe(
                    recipeManager, input, level, excludedRecipeIds);
            if (recipe != null) {
                return recipe;
            }
        }
        if (MOD.isAdvancedAeLoaded()) {
            RecipeHolder<DataRipperReassemblerRecipe> recipe = AdvancedAeReassemblerRecipeSource.findRecipe(
                    recipeManager, input, level, excludedRecipeIds);
            if (recipe != null) {
                return recipe;
            }
        }
        return null;
    }

    /** Resolves an optional integration recipe by the normalized identifier persisted by a machine channel. */
    public static @Nullable RecipeHolder<DataRipperReassemblerRecipe> findById(
                                                                               Level level,
                                                                               ResourceLocation recipeId) {
        RecipeManager recipeManager = level.getRecipeManager();
        if (MOD.isExtendedAeLoaded()) {
            RecipeHolder<DataRipperReassemblerRecipe> recipe = ExtendedAeReassemblerRecipeSource.findById(
                    recipeManager, recipeId);
            if (recipe != null) {
                return recipe;
            }
        }
        if (MOD.isAdvancedAeLoaded()) {
            RecipeHolder<DataRipperReassemblerRecipe> recipe = AdvancedAeReassemblerRecipeSource.findById(
                    recipeManager, recipeId);
            if (recipe != null) {
                return recipe;
            }
        }
        return null;
    }

    private static void appendAdditional(RecipeManager recipeManager,
                                         List<RecipeHolder<DataRipperReassemblerRecipe>> destination) {
        if (MOD.isExtendedAeLoaded()) {
            ExtendedAeReassemblerRecipeSource.appendRecipes(recipeManager, destination);
        }
        if (MOD.isAdvancedAeLoaded()) {
            AdvancedAeReassemblerRecipeSource.appendRecipes(recipeManager, destination);
        }
    }
}
