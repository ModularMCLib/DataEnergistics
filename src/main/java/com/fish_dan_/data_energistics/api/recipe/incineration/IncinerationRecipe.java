package com.fish_dan_.data_energistics.api.recipe.incineration;

import com.fish_dan_.data_energistics.common.data.DERecipes;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.List;
import java.util.Optional;

public final class IncinerationRecipe implements Recipe<IncinerationRecipeInput> {

    private final Block baseBlock;
    private final Optional<Block> transformedBlock;
    private final NonNullList<IncinerationIngredient> itemInputs;
    private final NonNullList<Ingredient> ingredients;
    private final NonNullList<ItemStack> results;

    public IncinerationRecipe(Block baseBlock, Optional<Block> transformedBlock, List<IncinerationIngredient> itemInputs, List<ItemStack> results) {
        this.baseBlock = baseBlock;
        this.transformedBlock = transformedBlock;
        if (transformedBlock.filter(block -> block.defaultBlockState().isAir()).isPresent()) {
            throw new IllegalArgumentException("Incineration transform block must not be air");
        }
        this.itemInputs = NonNullList.copyOf(itemInputs);
        this.ingredients = expandIngredients(itemInputs);
        this.results = NonNullList.copyOf(results);
    }

    @Override
    public boolean matches(IncinerationRecipeInput input, Level level) {
        if (!input.baseState().is(this.baseBlock)) {
            return false;
        }

        NonNullList<Ingredient> remaining = NonNullList.copyOf(this.ingredients);
        for (ItemStack stack : input.items()) {
            int available = stack.getCount();
            var iterator = remaining.iterator();
            while (iterator.hasNext() && available > 0) {
                if (iterator.next().test(stack)) {
                    iterator.remove();
                    available--;
                }
            }
            if (remaining.isEmpty()) {
                return true;
            }
        }

        return false;
    }

    @Override
    public ItemStack assemble(IncinerationRecipeInput input, HolderLookup.Provider registries) {
        return getResultItem(registries).copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return false;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return this.results.isEmpty() ? ItemStack.EMPTY : this.results.getFirst();
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        return this.ingredients;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return DERecipes.INCINERATION_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return DERecipes.INCINERATION_TYPE.get();
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    public Block getBaseBlock() {
        return this.baseBlock;
    }

    public Optional<Block> getTransformedBlock() {
        return this.transformedBlock;
    }

    public NonNullList<IncinerationIngredient> getItemInputs() {
        return this.itemInputs;
    }

    public NonNullList<ItemStack> getResults() {
        return this.results;
    }

    private static NonNullList<Ingredient> expandIngredients(List<IncinerationIngredient> itemInputs) {
        List<Ingredient> expanded = new ObjectArrayList<>();
        for (IncinerationIngredient input : itemInputs) {
            for (int index = 0; index < input.count(); index++) {
                expanded.add(input.ingredient());
            }
        }
        return NonNullList.copyOf(expanded);
    }
}
