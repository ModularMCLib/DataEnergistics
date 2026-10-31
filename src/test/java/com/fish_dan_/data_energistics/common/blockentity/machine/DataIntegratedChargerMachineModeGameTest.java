package com.fish_dan_.data_energistics.common.blockentity.machine;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.blockentity.machine.DataIntegratedChargerBlockEntity.MachineMode;
import com.fish_dan_.data_energistics.common.data.DEBlocks;
import com.fish_dan_.data_energistics.common.data.DEFluids;
import com.fish_dan_.data_energistics.common.data.DEItems;
import com.fish_dan_.data_energistics.common.recipe.chargepress.DataChargePressRecipeSupport;
import com.fish_dan_.data_energistics.common.recipe.charger.DataIntegratedChargerPatternModeResolver;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.core.definitions.AEItems;
import appeng.crafting.pattern.AEProcessingPattern;
import appeng.recipes.handlers.InscriberProcessType;
import appeng.recipes.handlers.InscriberRecipe;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import it.unimi.dsi.fastutil.objects.ObjectList;

@PrefixGameTestTemplate(false)
@GameTestHolder(Data_Energistics.MODID)
public final class DataIntegratedChargerMachineModeGameTest {

    private DataIntegratedChargerMachineModeGameTest() {}

    @TestHolder("data_integrated_charger_mode_button_cycles_all_processing_families")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void modeButtonCyclesAllProcessingFamilies(GameTestHelper helper) {
        assertEquals(MachineMode.CRYSTAL_GROWTH, MachineMode.POWDER.next());
        assertEquals(MachineMode.INSCRIBER, MachineMode.CRYSTAL_GROWTH.next());
        assertEquals(MachineMode.CHARGER, MachineMode.INSCRIBER.next());
        assertEquals(MachineMode.POWDER, MachineMode.CHARGER.next());
        assertEquals(MachineMode.POWDER, MachineMode.fromOrdinal(-1));
        assertEquals(MachineMode.POWDER, MachineMode.fromOrdinal(MachineMode.values().length));
        helper.succeed();
    }

    @TestHolder("data_integrated_charger_eae_circuit_cutter_rates")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void eaeCircuitCutterRatesFollowOriginalOutputCount(GameTestHelper helper) {
        assertEquals(9, DataIntegratedChargerBlockEntity.getEaeCircuitCutterResultCount(4));
        assertEquals(750, DataIntegratedChargerBlockEntity.getEaeCircuitCutterFluidAmount(4));
        assertEquals(19, DataIntegratedChargerBlockEntity.getEaeCircuitCutterResultCount(9));
        assertEquals(1_050, DataIntegratedChargerBlockEntity.getEaeCircuitCutterFluidAmount(9));
        helper.succeed();
    }

    @TestHolder("data_integrated_charger_concentrated_crystal_produces_thirteen_silicon_prints")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void concentratedCrystalProducesThirteenSiliconPrints(GameTestHelper helper) {
        assertCircuitBoardOperation(helper, DEItems.CONCENTRATED_SILICON_CARBON_METEORITE_CRYSTAL.toStack(2), 13);
        helper.succeed();
    }

    @TestHolder("data_integrated_charger_standard_silicon_still_produces_three_prints")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void standardSiliconStillProducesThreePrints(GameTestHelper helper) {
        assertCircuitBoardOperation(helper, new ItemStack(AEItems.SILICON.get(), 2), 3);
        helper.succeed();
    }

    @TestHolder("data_integrated_charger_processor_press_keeps_native_output_count")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void processorPressKeepsNativeOutputCount(GameTestHelper helper) {
        DataIntegratedChargerBlockEntity charger = createInscribingCharger(helper);
        charger.getInternalInventory().setItemDirect(0, DEItems.DATA_CIRCUIT_BOARD.toStack());
        charger.getInternalInventory().setItemDirect(1, DEItems.FLUIX_AGGREGATE_CRYSTAL_PROCESSOR.toStack());
        charger.getInternalInventory().setItemDirect(2, AEItems.SILICON_PRINT.stack());

        completeOperation(charger);

        ItemStack output = charger.getInternalInventory()
                .getStackInSlot(DataIntegratedChargerBlockEntity.ITEM_OUTPUT_START_SLOT);
        helper.assertTrue(output.is(DEItems.DATA_PROCESSOR.get()), "The native press must produce data processors");
        assertEquals(1, output.getCount());
        for (int slot = 0; slot < 3; slot++) {
            helper.assertTrue(charger.getInternalInventory().getStackInSlot(slot).isEmpty(),
                    "A PRESS operation must consume its original inputs exactly once");
        }
        helper.succeed();
    }

    @TestHolder("data_integrated_charger_native_inscribe_retains_reusable_press")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void nativeInscribeRetainsReusablePress(GameTestHelper helper) {
        DataIntegratedChargerBlockEntity charger = createInscribingCharger(helper);
        charger.getInternalInventory().setItemDirect(0, new ItemStack(Items.IRON_BLOCK));
        charger.getInternalInventory().setItemDirect(1, AEItems.SILICON_PRESS.stack());

        completeOperation(charger);

        ItemStack output = charger.getInternalInventory()
                .getStackInSlot(DataIntegratedChargerBlockEntity.ITEM_OUTPUT_START_SLOT);
        helper.assertTrue(output.is(AEItems.SILICON_PRESS.get()), "The INSCRIBE recipe must copy the silicon press");
        assertEquals(1, output.getCount());
        helper.assertTrue(charger.getInternalInventory().getStackInSlot(0).isEmpty(),
                "The INSCRIBE recipe must consume its middle input exactly once");
        ItemStack press = charger.getInternalInventory().getStackInSlot(1);
        helper.assertTrue(press.is(AEItems.SILICON_PRESS.get()), "The reusable press must remain installed");
        assertEquals(1, press.getCount());
        helper.succeed();
    }

    @TestHolder("data_integrated_charger_multi_input_recipe_uses_converted_output_count")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void multiInputRecipeUsesConvertedOutputCount(GameTestHelper helper) {
        DataIntegratedChargerBlockEntity charger = createInscribingCharger(helper);
        charger.getInternalInventory().setItemDirect(0, DEItems.DATA_PROCESSOR.toStack(6));
        charger.getInternalInventory().setItemDirect(1, AEItems.QUANTUM_ENTANGLED_SINGULARITY.stack());
        ItemStack cells = new ItemStack(DEBlocks.DATA_ENERGY_CELL.get());
        var recipe = helper.getLevel().getRecipeManager()
                .byKey(Data_Energistics.id("data_integrated_charger/data_energy_cell")).orElseThrow();
        var inputs = recipe.value().getIngredients();
        ItemStack denseCells = inputs.getFirst().getItems()[0].copyWithCount(12);
        charger.getInternalInventory().setItemDirect(2, denseCells);

        completeOperation(charger);

        ItemStack output = charger.getInternalInventory()
                .getStackInSlot(DataIntegratedChargerBlockEntity.ITEM_OUTPUT_START_SLOT);
        helper.assertTrue(ItemStack.isSameItemSameComponents(cells, output),
                "The multi-input recipe must preserve its native output item");
        assertEquals(3, output.getCount());
        for (int slot = 0; slot < 3; slot++) {
            helper.assertTrue(charger.getInternalInventory().getStackInSlot(slot).isEmpty(),
                    "The multi-input recipe must consume its declared inputs exactly once");
        }
        helper.succeed();
    }

    @TestHolder("data_charge_press_patterns_require_converted_output_counts")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void patternsRequireConvertedOutputCounts(GameTestHelper helper) {
        ObjectList<GenericStack> inputs = ObjectList.of(
                new GenericStack(AEItemKey.of(DEItems.CONCENTRATED_SILICON_CARBON_METEORITE_CRYSTAL.get()), 2),
                DataChargePressRecipeSupport.getFluidInput());
        for (int outputCount : new int[] { 3, 13 }) {
            ItemStack encoded = PatternDetailsHelper.encodeProcessingPattern(inputs,
                    ObjectList.of(new GenericStack(AEItemKey.of(AEItems.SILICON_PRINT.get()), outputCount)));
            int modeMask = DataIntegratedChargerPatternModeResolver.resolveModeMask(
                    helper.getLevel(), new AEProcessingPattern(AEItemKey.of(encoded)), null);
            assertEquals(outputCount == 13 ? 1 << MachineMode.INSCRIBER.ordinal() : 0, modeMask);
        }

        ObjectList<GenericStack> processorInputs = ObjectList.of(
                new GenericStack(AEItemKey.of(DEItems.DATA_CIRCUIT_BOARD.get()), 1),
                new GenericStack(AEItemKey.of(DEItems.FLUIX_AGGREGATE_CRYSTAL_PROCESSOR.get()), 1),
                new GenericStack(AEItemKey.of(AEItems.SILICON_PRINT.get()), 1));
        for (int outputCount : new int[] { 1, 3 }) {
            ItemStack encoded = PatternDetailsHelper.encodeProcessingPattern(processorInputs,
                    ObjectList.of(new GenericStack(AEItemKey.of(DEItems.DATA_PROCESSOR.get()), outputCount)));
            int modeMask = DataIntegratedChargerPatternModeResolver.resolveModeMask(
                    helper.getLevel(), new AEProcessingPattern(AEItemKey.of(encoded)), null);
            assertEquals(outputCount == 1 ? 1 << MachineMode.INSCRIBER.ordinal() : 0, modeMask);
        }

        ObjectList<GenericStack> templateInputs = ObjectList.of(
                new GenericStack(AEItemKey.of(Items.IRON_BLOCK), 1),
                new GenericStack(AEItemKey.of(AEItems.SILICON_PRESS.get()), 1));
        for (int outputCount : new int[] { 1, 3 }) {
            ItemStack encoded = PatternDetailsHelper.encodeProcessingPattern(templateInputs,
                    ObjectList.of(new GenericStack(AEItemKey.of(AEItems.SILICON_PRESS.get()), outputCount)));
            int modeMask = DataIntegratedChargerPatternModeResolver.resolveModeMask(
                    helper.getLevel(), new AEProcessingPattern(AEItemKey.of(encoded)), null);
            assertEquals(outputCount == 1 ? 1 << MachineMode.INSCRIBER.ordinal() : 0, modeMask);
        }
        helper.succeed();
    }

    @TestHolder("data_charge_press_bonus_scales_without_mutating_source_recipe")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void circuitBoardBonusScalesWithoutMutatingSourceRecipe(GameTestHelper helper) {
        for (int originalCount : new int[] { 1, 2, 6, 31, 32, 64 }) {
            ItemStack original = new ItemStack(AEItems.SILICON_PRINT.get(), originalCount);
            InscriberRecipe recipe = new InscriberRecipe(
                    Ingredient.of(DEItems.CONCENTRATED_SILICON_CARBON_METEORITE_CRYSTAL.get()), original,
                    Ingredient.of(AEItems.SILICON_PRESS.get()), Ingredient.EMPTY, InscriberProcessType.INSCRIBE);

            ItemStack result = DataChargePressRecipeSupport.getInscriberResult(recipe);
            assertEquals(originalCount * 2 + 1, result.getCount());
            assertEquals(result.getCount(), DataChargePressRecipeSupport.getTripleResult(recipe).getCount());
            assertEquals(result.getCount(), DataIntegratedChargerBlockEntity.getEaeCircuitCutterResultCount(originalCount));
            assertEquals(originalCount, original.getCount());
            helper.assertTrue(ItemStack.isSameItemSameComponents(original, result),
                    "The bonus must preserve the original output item and components");
        }

        for (ItemStack exemptOutput : new ItemStack[] {
                DEItems.DATA_PROCESSOR.toStack(2),
                new ItemStack(AEItems.LOGIC_PROCESSOR.get(), 2),
                new ItemStack(AEItems.SILICON_PRESS.get(), 2),
                DEItems.DATA_INSCRIBER_TEMPLATE.toStack(2)
        }) {
            ItemStack result = DataChargePressRecipeSupport.getInscriberResult(exemptOutput);
            assertEquals(2, result.getCount());
            helper.assertTrue(ItemStack.isSameItemSameComponents(exemptOutput, result),
                    "Processor and press-template outputs must retain their native counts");
        }
        helper.succeed();
    }

    private static void assertCircuitBoardOperation(GameTestHelper helper, ItemStack input, int expectedCount) {
        DataIntegratedChargerBlockEntity charger = createInscribingCharger(helper);
        charger.getInternalInventory().setItemDirect(0, input);
        charger.getFluidTank(0).setFluid(new FluidStack(DEFluids.DATA_CORROSION_LIQUID.get(), 250));

        completeOperation(charger);

        ItemStack output = charger.getInternalInventory()
                .getStackInSlot(DataIntegratedChargerBlockEntity.ITEM_OUTPUT_START_SLOT);
        helper.assertTrue(output.is(AEItems.SILICON_PRINT.get()), "The machine must produce silicon prints");
        assertEquals(expectedCount, output.getCount());
        helper.assertTrue(charger.getInternalInventory().getStackInSlot(0).isEmpty(),
                "The operation must consume exactly two input items");
        assertEquals(0, charger.getFluidTank(0).getFluidAmount());
        helper.assertTrue(charger.getCurrentAEPower() < 1.0E-4D,
                "The operation must retain its 1600 AE energy cost");
    }

    private static DataIntegratedChargerBlockEntity createInscribingCharger(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, DEBlocks.DATA_INTEGRATED_CHARGER.get());
        DataIntegratedChargerBlockEntity charger = (DataIntegratedChargerBlockEntity) helper.getBlockEntity(pos);
        charger.setMachineMode(MachineMode.INSCRIBER);
        charger.setInternalCurrentPower(1_600.0D);
        return charger;
    }

    private static void completeOperation(DataIntegratedChargerBlockEntity charger) {
        int duration = charger.getMaxProgress();
        for (int tick = 0; tick < duration; tick++) {
            charger.serverTick();
        }
    }

    private static void assertEquals(MachineMode expected, MachineMode actual) {
        if (expected != actual) {
            throw new GameTestAssertException("Expected " + expected + ", got " + actual);
        }
    }

    private static void assertEquals(int expected, int actual) {
        if (expected != actual) {
            throw new GameTestAssertException("Expected " + expected + ", got " + actual);
        }
    }
}
