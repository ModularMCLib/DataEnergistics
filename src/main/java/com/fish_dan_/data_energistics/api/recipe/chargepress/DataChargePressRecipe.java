package com.fish_dan_.data_energistics.api.recipe.chargepress;

import com.fish_dan_.data_energistics.api.recipe.lookup.CountedItemInputMatcher;
import com.fish_dan_.data_energistics.common.data.DERecipes;
import com.fish_dan_.data_energistics.common.recipe.chargepress.DataChargePressRecipeSupport;
import com.fish_dan_.data_energistics.util.FastUtilCollections;

import appeng.api.stacks.GenericStack;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;

import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectLists;

/**
 * A normalized crystal-growth operation with one to three item inputs and a fixed process fluid and module.
 */
public record DataChargePressRecipe(ObjectList<DataChargePressIngredient> inputs, int fluidAmount,
                                    ItemStack result)
        implements Recipe<DataChargePressRecipeInput> {

    public static final int MIN_ITEM_INPUT_COUNT = 1;
    public static final int MAX_ITEM_INPUT_COUNT = 3;
    public static final int MAX_FLUID_AMOUNT = 51_200;

    public DataChargePressRecipe(ObjectList<DataChargePressIngredient> inputs, int fluidAmount, ItemStack result) {
        if (inputs.isEmpty() || inputs.size() > MAX_ITEM_INPUT_COUNT) {
            throw new IllegalArgumentException(
                    "Data charge press recipes require between " + MIN_ITEM_INPUT_COUNT + " and " +
                            MAX_ITEM_INPUT_COUNT + " inputs: " + inputs.size());
        }
        if (fluidAmount <= 0 || fluidAmount > MAX_FLUID_AMOUNT) {
            throw new IllegalArgumentException(
                    "Data charge press fluid amount must be between 1 and " + MAX_FLUID_AMOUNT + ": " + fluidAmount);
        }
        if (result.isEmpty()) {
            throw new IllegalArgumentException("Data charge press result must not be empty");
        }
        this.inputs = FastUtilCollections.immutableList(inputs);
        this.fluidAmount = fluidAmount;
        this.result = result.copy();
    }

    @Override
    public boolean matches(DataChargePressRecipeInput input, Level level) {
        return getModule().test(input.module()) && matchesMachineInputs(input.items(), input.fluid());
    }

    @Override
    public ItemStack assemble(DataChargePressRecipeInput input, HolderLookup.Provider registries) {
        return this.getResultItem(registries).copy();
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

    public GenericStack getFluidInput() {
        return DataChargePressRecipeSupport.getFluidInput(this.fluidAmount);
    }

    public Ingredient getModule() {
        return DataChargePressRecipeSupport.CRYSTAL_GROWTH_MODULES;
    }

    @Override
    public ItemStack result() {
        return this.result.copy();
    }

    /** Finds physical input slots and their required consumption counts after aggregating identical stacks. */
    public ObjectList<InputSlot> findMatchingInputSlots(ObjectList<ItemStack> inputs) {
        ObjectList<CountedItemInputMatcher.Assignment> assignments = CountedItemInputMatcher.match(
                this.inputs,
                inputs);
        ObjectList<InputSlot> matches = CountedItemInputMatcher.mapAssignments(assignments, InputSlot::new);
        return matches == null ? ObjectLists.emptyList() : matches;
    }

    /** Matches consumable inputs after the machine has selected its crystal-growth mode. */
    public boolean matchesMachineInputs(ObjectList<ItemStack> items, FluidStack fluid) {
        return matchesFluid(fluid) && !findMatchingInputSlots(items).isEmpty();
    }

    private boolean matchesFluid(FluidStack fluid) {
        return DataChargePressRecipeSupport.matchesFluid(fluid, this.fluidAmount);
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return DERecipes.DATA_CHARGE_PRESS_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return DERecipes.DATA_CHARGE_PRESS_TYPE.get();
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
