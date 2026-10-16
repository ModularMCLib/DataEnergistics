package com.fish_dan_.data_energistics.recipe.reassembler;

import com.fish_dan_.data_energistics.util.FastUtilCollections;

import appeng.api.stacks.GenericStack;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeInput;

import it.unimi.dsi.fastutil.objects.ObjectList;
import org.jspecify.annotations.Nullable;

public record DataRipperReassemblerRecipeInput(ObjectList<ItemStack> items, ObjectList<GenericStack> fluidInputs,
                                               ObjectList<@Nullable GenericStack> keyInputs)
        implements RecipeInput {

    public DataRipperReassemblerRecipeInput(ObjectList<ItemStack> items, ObjectList<GenericStack> fluidInputs,
                                            ObjectList<@Nullable GenericStack> keyInputs) {
        this.items = FastUtilCollections.immutableList(items);
        this.fluidInputs = FastUtilCollections.immutableList(fluidInputs);
        this.keyInputs = FastUtilCollections.immutableList(keyInputs);
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
