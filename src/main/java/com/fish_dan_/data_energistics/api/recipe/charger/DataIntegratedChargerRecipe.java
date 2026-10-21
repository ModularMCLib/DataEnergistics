package com.fish_dan_.data_energistics.api.recipe.charger;

import com.fish_dan_.data_energistics.api.recipe.chargepress.DataChargePressIngredient;
import com.fish_dan_.data_energistics.api.recipe.lookup.CountedItemInputMatcher;
import com.fish_dan_.data_energistics.common.data.DERecipes;
import com.fish_dan_.data_energistics.util.FastUtilCollections;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectLists;

/**
 * A multi-input recipe executed by the data integrated charger's inscribing mode.
 */
public record DataIntegratedChargerRecipe(ObjectList<DataChargePressIngredient> inputs,
                                          ItemStack result)
        implements Recipe<DataIntegratedChargerRecipeInput> {

    public static final int MIN_ITEM_INPUT_COUNT = 1;
    public static final int MAX_ITEM_INPUT_COUNT = 3;

    public DataIntegratedChargerRecipe(ObjectList<DataChargePressIngredient> inputs, ItemStack result) {
        if (inputs.isEmpty() || inputs.size() > MAX_ITEM_INPUT_COUNT) {
            throw new IllegalArgumentException(
                    "Data integrated charger recipes require between " + MIN_ITEM_INPUT_COUNT + " and " +
                            MAX_ITEM_INPUT_COUNT + " inputs: " + inputs.size());
        }
        if (result.isEmpty()) {
            throw new IllegalArgumentException("Data integrated charger result must not be empty");
        }
        this.inputs = FastUtilCollections.immutableList(inputs);
        this.result = result.copy();
    }

    @Override
    public boolean matches(DataIntegratedChargerRecipeInput input, Level level) {
        return !findMatchingInputSlots(input.items()).isEmpty();
    }

    @Override
    public ItemStack assemble(DataIntegratedChargerRecipeInput input, HolderLookup.Provider registries) {
        return getResultItem(registries).copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return this.result.copy();
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        NonNullList<Ingredient> ingredients = NonNullList.create();
        for (DataChargePressIngredient input : this.inputs) {
            ingredients.add(input.ingredient());
        }
        return ingredients;
    }

    @Override
    public ItemStack result() {
        return this.result.copy();
    }

    /** Finds physical input slots and their required consumption counts after aggregating identical stacks. */
    public ObjectList<InputSlot> findMatchingInputSlots(ObjectList<ItemStack> availableInputs) {
        ObjectList<CountedItemInputMatcher.Assignment> assignments = CountedItemInputMatcher.match(
                this.inputs,
                availableInputs);
        ObjectList<InputSlot> matches = CountedItemInputMatcher.mapAssignments(assignments, InputSlot::new);
        return matches == null ? ObjectLists.emptyList() : matches;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return DERecipes.DATA_INTEGRATED_CHARGER_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return DERecipes.DATA_INTEGRATED_CHARGER_TYPE.get();
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    /**
     * One physical input slot selected for this operation and the number of items to consume from it.
     */
    public record InputSlot(int slot, int count) {}
}
