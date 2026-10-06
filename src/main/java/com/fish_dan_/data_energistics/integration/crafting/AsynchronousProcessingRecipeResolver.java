package com.fish_dan_.data_energistics.integration.crafting;

import com.fish_dan_.data_energistics.integration.MOD;
import com.fish_dan_.data_energistics.integration.ae.advancedae.reassembler.AdvancedAeReassemblerRecipeResolver;
import com.fish_dan_.data_energistics.integration.ae.extendedae.reassembler.ExtendedAeReassemblerRecipeResolver;
import com.fish_dan_.data_energistics.recipe.ProcessingRecipeResolver;
import com.fish_dan_.data_energistics.recipe.reassembler.DataReassemblerRecipeResolver;
import com.fish_dan_.data_energistics.recipe.reassembler.DataRipperReassemblerRecipe;
import com.fish_dan_.data_energistics.recipe.reassembler.DataRipperReassemblerRecipeInput;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;

import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.jspecify.annotations.Nullable;

/**
 * Superset recipe resolver for the asynchronous processing factory.
 *
 * <p>
 * The factory executes native data reassembler recipes as well as recipes read directly from the loaded EAE and
 * AAE recipe tables. It does not introduce a recipe type of its own.
 * </p>
 */
public final class AsynchronousProcessingRecipeResolver implements ProcessingRecipeResolver {

    public static final AsynchronousProcessingRecipeResolver INSTANCE = new AsynchronousProcessingRecipeResolver();

    private AsynchronousProcessingRecipeResolver() {}

    @Override
    public @Nullable RecipeHolder<DataRipperReassemblerRecipe> find(
                                                                    Level level,
                                                                    DataRipperReassemblerRecipeInput input,
                                                                    ObjectSet<ResourceLocation> excludedRecipeIds) {
        RecipeHolder<DataRipperReassemblerRecipe> nativeRecipe = DataReassemblerRecipeResolver.INSTANCE.find(
                level, input, excludedRecipeIds);
        if (nativeRecipe != null) {
            return nativeRecipe;
        }

        RecipeManager recipeManager = level.getRecipeManager();
        if (MOD.isExtendedAeLoaded()) {
            RecipeHolder<DataRipperReassemblerRecipe> recipe = ExtendedAeReassemblerRecipeResolver.findRecipe(
                    recipeManager, input, level, excludedRecipeIds);
            if (recipe != null) {
                return recipe;
            }
        }
        if (MOD.isAdvancedAeLoaded()) {
            return AdvancedAeReassemblerRecipeResolver.findRecipe(
                    recipeManager, input, level, excludedRecipeIds);
        }
        return null;
    }

    @Override
    public @Nullable RecipeHolder<DataRipperReassemblerRecipe> findById(
                                                                        Level level,
                                                                        ResourceLocation recipeId,
                                                                        DataRipperReassemblerRecipeInput input) {
        RecipeHolder<DataRipperReassemblerRecipe> nativeRecipe = DataReassemblerRecipeResolver.INSTANCE.findById(
                level, recipeId, input);
        if (nativeRecipe != null) {
            return nativeRecipe;
        }

        RecipeManager recipeManager = level.getRecipeManager();
        if (MOD.isExtendedAeLoaded()) {
            RecipeHolder<DataRipperReassemblerRecipe> recipe = ExtendedAeReassemblerRecipeResolver.findById(
                    recipeManager, recipeId, input, level);
            if (recipe != null) {
                return recipe;
            }
        }
        if (MOD.isAdvancedAeLoaded()) {
            return AdvancedAeReassemblerRecipeResolver.findById(
                    recipeManager, recipeId, input, level);
        }
        return null;
    }
}
