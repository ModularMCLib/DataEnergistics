package com.fish_dan_.data_energistics.api.recipe.chargepress;

import com.fish_dan_.data_energistics.util.FastUtilCollections;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeInput;
import net.neoforged.neoforge.fluids.FluidStack;

import it.unimi.dsi.fastutil.objects.ObjectList;

/** Runtime inputs for a data charge press recipe. */
public record DataChargePressRecipeInput(ObjectList<ItemStack> items, FluidStack fluid, ItemStack module)
        implements RecipeInput {

    public DataChargePressRecipeInput(ObjectList<ItemStack> items, FluidStack fluid, ItemStack module) {
        this.items = FastUtilCollections.immutableList(items);
        fluid = fluid.copy();
        module = module.copy();
        this.fluid = fluid;
        this.module = module;
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
