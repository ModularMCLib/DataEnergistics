package com.fish_dan_.data_energistics.common.crafting.packaged.recipe;

import appeng.api.stacks.KeyCounter;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import org.jspecify.annotations.Nullable;

/** Maps exact supplied ingredients to a physical grid, retaining holes and the declared shaped width. */
public final class PackagedCraftingGrid {

    private PackagedCraftingGrid() {}

    public static @Nullable ObjectList<ItemStack> assign(ObjectList<Ingredient> ingredients, int width, int gridSize,
                                                         KeyCounter[] inputs) {
        if (width <= 0 || width > gridSize || ingredients.size() > width * gridSize) return null;
        var nonEmpty = new ObjectArrayList<Ingredient>();
        for (var ingredient : ingredients) if (!ingredient.isEmpty()) nonEmpty.add(ingredient);
        var matched = PackagedIngredientAssignment.match(nonEmpty, inputs);
        if (matched == null) return null;
        var grid = new ObjectArrayList<ItemStack>(gridSize * gridSize);
        for (int slot = 0; slot < gridSize * gridSize; slot++) grid.add(ItemStack.EMPTY);
        int next = 0;
        for (int slot = 0; slot < ingredients.size(); slot++) {
            if (!ingredients.get(slot).isEmpty()) grid.set(slot / width * gridSize + slot % width, matched.get(next++));
        }
        return grid;
    }
}
