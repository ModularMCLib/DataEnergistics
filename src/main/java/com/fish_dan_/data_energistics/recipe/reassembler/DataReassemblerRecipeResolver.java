package com.fish_dan_.data_energistics.recipe.reassembler;

import com.fish_dan_.data_energistics.recipe.ProcessingRecipeResolver;
import com.fish_dan_.data_energistics.registry.DERecipes;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;

/** Resolves the native recipe table owned by the standalone data reassembler. */
public final class DataReassemblerRecipeResolver implements ProcessingRecipeResolver {

    public static final DataReassemblerRecipeResolver INSTANCE = new DataReassemblerRecipeResolver();

    private DataReassemblerRecipeResolver() {}

    /** Returns the native data reassembler table in deterministic ID order. */
    public ObjectList<RecipeHolder<DataRipperReassemblerRecipe>> recipes(RecipeManager recipeManager) {
        ObjectArrayList<RecipeHolder<DataRipperReassemblerRecipe>> recipes = new ObjectArrayList<>(
                recipeManager.getAllRecipesFor(DERecipes.DATA_RIPPER_REASSEMBLER_TYPE.get()));
        recipes.sort(Comparator.comparing(holder -> holder.id().toString()));
        return ObjectLists.unmodifiable(recipes);
    }

    @Override
    public @Nullable RecipeHolder<DataRipperReassemblerRecipe> find(
                                                                    Level level,
                                                                    DataRipperReassemblerRecipeInput input,
                                                                    ObjectSet<ResourceLocation> excludedRecipeIds) {
        for (RecipeHolder<DataRipperReassemblerRecipe> holder : recipes(level.getRecipeManager())) {
            if (!excludedRecipeIds.contains(holder.id()) && holder.value().matches(input, level)) {
                return holder;
            }
        }
        return null;
    }

    @Override
    public @Nullable RecipeHolder<DataRipperReassemblerRecipe> findById(
                                                                        Level level,
                                                                        ResourceLocation recipeId,
                                                                        DataRipperReassemblerRecipeInput input) {
        RecipeHolder<?> holder = level.getRecipeManager().byKey(recipeId).orElse(null);
        if (holder == null || !(holder.value() instanceof DataRipperReassemblerRecipe recipe) ||
                !recipe.matches(input, level)) {
            return null;
        }
        return new RecipeHolder<>(holder.id(), recipe);
    }
}
