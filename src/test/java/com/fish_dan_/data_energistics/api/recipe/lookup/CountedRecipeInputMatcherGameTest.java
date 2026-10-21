package com.fish_dan_.data_energistics.api.recipe.lookup;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.recipe.chargepress.DataChargePressIngredient;
import com.fish_dan_.data_energistics.api.recipe.chargepress.DataChargePressRecipe;
import com.fish_dan_.data_energistics.api.recipe.charger.DataIntegratedChargerRecipe;
import com.fish_dan_.data_energistics.api.recipe.reassembler.DataReassemblerItemOutput;
import com.fish_dan_.data_energistics.api.recipe.reassembler.DataRipperReassemblerIngredient;
import com.fish_dan_.data_energistics.api.recipe.reassembler.DataRipperReassemblerRecipe;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectLists;

@PrefixGameTestTemplate(false)
@GameTestHolder(Data_Energistics.MODID)
public final class CountedRecipeInputMatcherGameTest {

    private CountedRecipeInputMatcherGameTest() {}

    @TestHolder("counted_recipe_inputs_aggregate_identical_stacks_across_slots")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void countedRecipeInputsAggregateIdenticalStacksAcrossSlots(GameTestHelper helper) {
        Ingredient iron = Ingredient.of(Items.IRON_INGOT);
        ObjectList<ItemStack> splitInputs = new ObjectArrayList<>();
        splitInputs.add(new ItemStack(Items.IRON_INGOT));
        splitInputs.add(new ItemStack(Items.IRON_INGOT));

        DataChargePressRecipe chargePressRecipe = new DataChargePressRecipe(
                twoRequirements(iron),
                1,
                new ItemStack(Items.DIAMOND));
        ObjectList<DataChargePressRecipe.InputSlot> chargePressMatches = chargePressRecipe.findMatchingInputSlots(splitInputs);
        assertEquals(2, chargePressMatches.size(), "Data charge press must reserve both physical slots");
        assertEquals(1, chargePressMatches.get(0).count(), "Data charge press must reserve one item per slot");
        assertEquals(1, chargePressMatches.get(1).count(), "Data charge press must reserve one item per slot");

        DataIntegratedChargerRecipe integratedChargerRecipe = new DataIntegratedChargerRecipe(
                twoRequirements(iron),
                new ItemStack(Items.DIAMOND));
        ObjectList<DataIntegratedChargerRecipe.InputSlot> integratedMatches = integratedChargerRecipe.findMatchingInputSlots(splitInputs);
        assertEquals(2, integratedMatches.size(), "Integrated charger must reserve both physical slots");

        DataRipperReassemblerRecipe reassemblerRecipe = new DataRipperReassemblerRecipe(
                ObjectLists.singleton(new DataRipperReassemblerIngredient(iron, 2)),
                ObjectLists.emptyList(),
                ObjectLists.singleton(new DataReassemblerItemOutput(new ItemStack(Items.DIAMOND), null)),
                ObjectLists.emptyList(),
                1,
                null,
                null);
        ObjectList<DataRipperReassemblerRecipe.ItemInputAssignment> reassemblerMatches = reassemblerRecipe.findMatchingItemInputs(splitInputs);
        assertEquals(2, reassemblerMatches.size(), "Data reassembler must reserve both physical slots");
        helper.succeed();
    }

    private static ObjectList<DataChargePressIngredient> twoRequirements(Ingredient ingredient) {
        ObjectList<DataChargePressIngredient> requirements = new ObjectArrayList<>();
        requirements.add(new DataChargePressIngredient(ingredient, 1));
        requirements.add(new DataChargePressIngredient(ingredient, 1));
        return requirements;
    }

    private static void assertEquals(int expected, int actual, String message) {
        if (expected != actual) {
            throw new GameTestAssertException(message + ": expected " + expected + ", got " + actual);
        }
    }
}
