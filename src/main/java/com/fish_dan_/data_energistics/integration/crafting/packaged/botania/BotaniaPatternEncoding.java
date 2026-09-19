package com.fish_dan_.data_energistics.integration.crafting.packaged.botania;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jspecify.annotations.Nullable;
import vazkii.botania.api.recipe.PetalApothecaryRecipe;
import vazkii.botania.api.recipe.RunicAltarRecipe;
import vazkii.botania.common.crafting.recipe.RecipeUtils;

import java.util.List;

/** Pure expansion of an exact recipe's selected ingredients into the physical inputs and native returned items. */
public final class BotaniaPatternEncoding {

    private BotaniaPatternEncoding() {}

    public static @Nullable EncodedRecipe augment(ServerLevel level, RecipeHolder<?> holder, List<ItemStack> recipeInputs) {
        var inputs = new ObjectArrayList<ItemStack>();
        for (ItemStack stack : recipeInputs) {
            if (stack.isEmpty() || stack.getCount() > 64 || inputs.size() + stack.getCount() > 64) return null;
            for (int i = 0; i < stack.getCount(); i++) inputs.add(stack.copyWithCount(1));
        }
        var outputs = new ObjectArrayList<ItemStack>();
        Ingredient reagent;
        if (holder.value() instanceof PetalApothecaryRecipe recipe) {
            var input = RecipeUtils.getInputFromListWithoutUnstacking(inputs);
            if (!recipe.matches(input, level)) return null;
            outputs.add(recipe.assemble(input, level.registryAccess()));
            reagent = recipe.getReagent();
            inputs.add(new ItemStack(Items.WATER_BUCKET));
            outputs.add(new ItemStack(Items.BUCKET));
        } else if (holder.value() instanceof RunicAltarRecipe recipe) {
            var input = RecipeUtils.getInputFromListWithoutUnstacking(inputs);
            if (!recipe.matches(input, level)) {
                if (inputs.size() != recipe.getIngredients().size()) return null;
                for (Ingredient catalyst : recipe.getCatalysts()) {
                    ItemStack candidate = candidate(catalyst);
                    if (candidate.isEmpty()) return null;
                    inputs.add(candidate);
                }
                input = RecipeUtils.getInputFromListWithoutUnstacking(inputs);
                if (!recipe.matches(input, level)) return null;
            }
            outputs.add(recipe.assemble(input, level.registryAccess()));
            for (var remaining : recipe.getRemainingItems(input)) if (!remaining.isEmpty()) outputs.add(remaining.copy());
            reagent = recipe.getReagent();
        } else return null;
        ItemStack selectedReagent = candidate(reagent);
        if (selectedReagent.isEmpty() || outputs.stream().anyMatch(ItemStack::isEmpty)) return null;
        inputs.add(selectedReagent);
        return new EncodedRecipe(List.copyOf(inputs), List.copyOf(outputs));
    }

    private static ItemStack candidate(Ingredient ingredient) {
        for (ItemStack candidate : ingredient.getItems()) {
            if (!candidate.isEmpty() && ingredient.test(candidate)) return candidate.copyWithCount(1);
        }
        return ItemStack.EMPTY;
    }

    /** Lists belong to the returned value and contain copies; consumers must not mutate their component data. */
    public record EncodedRecipe(List<ItemStack> inputs, List<ItemStack> outputs) {}
}
