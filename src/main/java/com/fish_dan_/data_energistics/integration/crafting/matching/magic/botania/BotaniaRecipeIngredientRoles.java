package com.fish_dan_.data_energistics.integration.crafting.matching.magic.botania;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.crafting.matching.RecipeMatchingRuleAdapter;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import vazkii.botania.api.recipe.PetalApothecaryRecipe;
import vazkii.botania.api.recipe.RunicAltarRecipe;

/**
 * Optional Botania ingredient roles omitted by the ordinary recipe ingredient list.
 */
@NullMarked
public final class BotaniaRecipeIngredientRoles implements RecipeMatchingRuleAdapter {

    @Override
    public ResourceLocation id() {
        return Data_Energistics.id("botania_recipe_matching");
    }

    @Override
    public @Nullable ObjectList<Ingredient> inputIngredients(ServerLevel level, ResourceLocation recipeId) {
        var holder = level.getRecipeManager().byKey(recipeId);
        return holder.isPresent() ? resolve(holder.get().value()) : null;
    }

    @Override
    public @Nullable ObjectList<Ingredient> outputIngredients(ServerLevel level, ResourceLocation recipeId) {
        var holder = level.getRecipeManager().byKey(recipeId);
        if (holder.isEmpty()) return null;
        if (holder.get().value() instanceof RunicAltarRecipe rune) {
            var outputs = new ObjectArrayList<>(rune.getCatalysts());
            outputs.add(Ingredient.of(rune.getResultItem(level.registryAccess())));
            return ObjectLists.unmodifiable(outputs);
        }
        return holder.get().value() instanceof PetalApothecaryRecipe ? ObjectList.of() : null;
    }

    private static @Nullable ObjectList<Ingredient> resolve(Recipe<?> recipe) {
        if (!(recipe instanceof PetalApothecaryRecipe || recipe instanceof RunicAltarRecipe)) return null;
        var roles = new ObjectArrayList<>(recipe.getIngredients());
        if (recipe instanceof PetalApothecaryRecipe petal) {
            roles.add(petal.getReagent());
            roles.add(Ingredient.of(Items.WATER_BUCKET));
        } else if (recipe instanceof RunicAltarRecipe rune) {
            roles.addAll(rune.getCatalysts());
            roles.add(rune.getReagent());
        }
        return ObjectLists.unmodifiable(roles);
    }
}
