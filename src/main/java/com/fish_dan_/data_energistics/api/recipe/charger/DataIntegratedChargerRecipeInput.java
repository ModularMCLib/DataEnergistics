package com.fish_dan_.data_energistics.api.recipe.charger;

import com.fish_dan_.data_energistics.util.FastUtilCollections;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeInput;

import it.unimi.dsi.fastutil.objects.ObjectList;

/** Runtime item inputs for a data integrated charger recipe. */
public record DataIntegratedChargerRecipeInput(ObjectList<ItemStack> items) implements RecipeInput {

    public DataIntegratedChargerRecipeInput(ObjectList<ItemStack> items) {
        this.items = FastUtilCollections.immutableList(items);
    }

    @Override
    public ItemStack getItem(int index) {
        return this.items.get(index);
    }

    @Override
    public int size() {
        return this.items.size();
    }
}
