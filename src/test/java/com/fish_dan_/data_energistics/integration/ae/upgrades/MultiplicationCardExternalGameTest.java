package com.fish_dan_.data_energistics.integration.ae.upgrades;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.data.DEItems;

import appeng.api.upgrades.IUpgradeableObject;
import appeng.blockentity.AEBaseInvBlockEntity;
import appeng.core.definitions.AEItems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

/** Optional-mod cases execute when their integration is present in the test run. */
@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class MultiplicationCardExternalGameTest {

    private static final BlockPos MACHINE_POS = new BlockPos(1, 1, 1);

    private MultiplicationCardExternalGameTest() {}

    @TestHolder("multiplication_card_ae2_inscriber_produces_nine_prints")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5", timeoutTicks = 500)
    public static void ae2InscriberProducesNinePrints(GameTestHelper helper) {
        runItemRecipe(helper, "ae2:inscriber", 3, 9,
                new ItemStack[] {AEItems.SILICON_PRESS.stack(), ItemStack.EMPTY, DEItems.CONCENTRATED_SILICON_CARBON_METEORITE_CRYSTAL.toStack()}, FluidStack.EMPTY);
    }

    @TestHolder("multiplication_card_eae_extended_inscriber_produces_nine_prints")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5", timeoutTicks = 500)
    public static void extendedInscriberProducesNinePrints(GameTestHelper helper) {
        if (skipAbsentMod(helper, "extendedae")) return;
        runItemRecipe(helper, "extendedae:ex_inscriber", 3, 9,
                new ItemStack[] {AEItems.SILICON_PRESS.stack(), ItemStack.EMPTY, DEItems.CONCENTRATED_SILICON_CARBON_METEORITE_CRYSTAL.toStack()}, FluidStack.EMPTY);
    }

    @TestHolder("multiplication_card_eae_circuit_cutter_rounds_nine_up_to_fourteen")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5", timeoutTicks = 500)
    public static void extendedCircuitCutterRoundsUp(GameTestHelper helper) {
        if (skipAbsentMod(helper, "extendedae")) return;
        runItemRecipe(helper, "extendedae:circuit_cutter", 1, 14, new ItemStack[] {new ItemStack(Items.GOLD_BLOCK)}, FluidStack.EMPTY);
    }

    @TestHolder("multiplication_card_eae_crystal_assembler_produces_twelve_crystals")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5", timeoutTicks = 500)
    public static void extendedCrystalAssemblerProducesTwelve(GameTestHelper helper) {
        if (skipAbsentMod(helper, "extendedae")) return;
        runItemRecipe(helper, "extendedae:crystal_assembler", 9, 12, crystalInputs(), new FluidStack(Fluids.WATER, 100));
    }

    @TestHolder("multiplication_card_eaep_super_circuit_cutter_rounds_nine_up_to_fourteen")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5", timeoutTicks = 500)
    public static void superCircuitCutterRoundsUp(GameTestHelper helper) {
        if (skipAbsentMod(helper, "extendedae_plus")) return;
        runItemRecipe(helper, "extendedae_plus:circuit_cutter_plus", 1, 14, new ItemStack[] {new ItemStack(Items.GOLD_BLOCK)}, FluidStack.EMPTY);
    }

    @TestHolder("multiplication_card_eaep_super_crystal_assembler_produces_twelve_crystals")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5", timeoutTicks = 500)
    public static void superCrystalAssemblerProducesTwelve(GameTestHelper helper) {
        if (skipAbsentMod(helper, "extendedae_plus")) return;
        runItemRecipe(helper, "extendedae_plus:crystal_assembler_plus", 9, 12, crystalInputs(), new FluidStack(Fluids.WATER, 100));
    }

    @TestHolder("multiplication_card_aae_reaction_chamber_produces_three_singularities")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5", timeoutTicks = 500)
    public static void reactionChamberBoostsItemOutput(GameTestHelper helper) {
        if (skipAbsentMod(helper, "advanced_ae")) return;
        ItemStack skyDust = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("ae2", "sky_dust")), 2);
        runItemRecipe(helper, "advanced_ae:reaction_chamber", 9, 3,
                new ItemStack[] {AEItems.SINGULARITY.stack(), AEItems.ENDER_DUST.stack(2), skyDust}, new FluidStack(Fluids.LAVA, 100));
    }

    @TestHolder("multiplication_card_aae_reaction_chamber_produces_1500mb_fluid")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5", timeoutTicks = 500)
    public static void reactionChamberBoostsFluidOutput(GameTestHelper helper) {
        if (skipAbsentMod(helper, "advanced_ae")) return;
        AEBaseInvBlockEntity machine = placeMachine(helper, "advanced_ae:reaction_chamber");
        machine.getInternalInventory().setItemDirect(0, new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("advanced_ae", "quantum_infused_dust"))));
        IFluidHandler fluids = helper.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, helper.absolutePos(MACHINE_POS), null);
        helper.assertTrue(fluids != null, "Reaction chamber must expose fluid storage");
        helper.assertValueEqual(fluids.fill(new FluidStack(Fluids.WATER, 4_000), IFluidHandler.FluidAction.EXECUTE), 4_000, "Recipe input water must fit");
        helper.startSequence().thenWaitUntil(() -> {
            int produced = 0;
            for (int tank = 0; tank < fluids.getTanks(); tank++) {
                FluidStack stack = fluids.getFluidInTank(tank);
                if (!stack.isEmpty() && !stack.is(Fluids.WATER)) produced += stack.getAmount();
            }
            helper.assertValueEqual(produced, 1_500, "1000 mB must become 1500 mB");
        }).thenSucceed();
    }

    private static void runItemRecipe(GameTestHelper helper, String blockId, int outputSlot, int expected,
                                      ItemStack[] inputs, FluidStack fluidInput) {
        AEBaseInvBlockEntity machine = placeMachine(helper, blockId);
        for (int slot = 0; slot < inputs.length; slot++) machine.getInternalInventory().setItemDirect(slot, inputs[slot]);
        if (!fluidInput.isEmpty()) {
            IFluidHandler fluids = helper.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, helper.absolutePos(MACHINE_POS), null);
            helper.assertTrue(fluids != null, "Machine must expose its recipe fluid input");
            helper.assertValueEqual(fluids.fill(fluidInput, IFluidHandler.FluidAction.EXECUTE), fluidInput.getAmount(), "Recipe input fluid must fit");
        }
        helper.startSequence().thenWaitUntil(() -> {
            helper.assertValueEqual(machine.getInternalInventory().getStackInSlot(outputSlot).getCount(), expected, "Installed card must boost the actual machine output");
        }).thenSucceed();
    }

    @TestHolder("multiplication_card_eae_crystal_output_buffer_holds_96")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5", timeoutTicks = 1_200)
    public static void extendedCrystalBufferHoldsNinetySix(GameTestHelper helper) {
        if (skipAbsentMod(helper, "extendedae")) return;
        runItemRecipe(helper, "extendedae:crystal_assembler", 9, 96,
                new ItemStack[] {AEItems.CERTUS_QUARTZ_CRYSTAL_CHARGED.stack(32), new ItemStack(Items.REDSTONE, 32), new ItemStack(Items.QUARTZ, 32)},
                new FluidStack(Fluids.WATER, 800));
    }

    @TestHolder("multiplication_card_eaep_parallel_output_buffer_holds_96")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5", timeoutTicks = 800)
    public static void superCrystalBufferHoldsNinetySix(GameTestHelper helper) {
        if (skipAbsentMod(helper, "extendedae_plus")) return;
        runItemRecipe(helper, "extendedae_plus:crystal_assembler_plus", 9, 96,
                new ItemStack[] {AEItems.CERTUS_QUARTZ_CRYSTAL_CHARGED.stack(32), new ItemStack(Items.REDSTONE, 32), new ItemStack(Items.QUARTZ, 32)},
                new FluidStack(Fluids.WATER, 800));
    }

    @TestHolder("multiplication_card_aae_96_outputs_survive_rejected_export_removal_and_nbt")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5", timeoutTicks = 1_200)
    public static void reactionChamberPreservesNinetySixAfterRemovalAndReload(GameTestHelper helper) {
        if (skipAbsentMod(helper, "advanced_ae")) return;
        helper.setBlock(new BlockPos(2, 1, 1), Blocks.CHEST);
        ChestBlockEntity chest = helper.getBlockEntity(new BlockPos(2, 1, 1));
        for (int slot = 0; slot < chest.getContainerSize(); slot++) chest.setItem(slot, new ItemStack(Items.STONE, 64));
        AEBaseInvBlockEntity machine = placeMachine(helper, "advanced_ae:reaction_chamber");
        machine.getInternalInventory().setItemDirect(0, AEItems.CERTUS_QUARTZ_CRYSTAL.stack(64));
        IFluidHandler fluids = helper.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, helper.absolutePos(MACHINE_POS), null);
        helper.assertTrue(fluids != null, "Reaction chamber must expose fluid storage");
        helper.assertValueEqual(fluids.fill(new FluidStack(Fluids.WATER, 1_000), IFluidHandler.FluidAction.EXECUTE), 1_000, "Recipe input must fit");
        helper.startSequence().thenWaitUntil(() -> {
            helper.assertValueEqual(machine.getInternalInventory().getStackInSlot(9).getCount(), 96, "A native full stack must produce 96");
        }).thenExecute(() -> {
            ((IUpgradeableObject) machine).getUpgrades().setItemDirect(0, ItemStack.EMPTY);
            CompoundTag saved = machine.saveWithFullMetadata(helper.getLevel().registryAccess());
            machine.loadWithComponents(saved, helper.getLevel().registryAccess());
        }).thenExecuteAfter(40, () -> {
            helper.assertValueEqual(machine.getInternalInventory().getStackInSlot(9).getCount(), 96, "Rejected exports and reload must preserve all outputs after removing the card");
            helper.assertValueEqual(machine.getInternalInventory().extractItem(9, Integer.MAX_VALUE, false).getCount(), 64, "The first extraction must respect ordinary stack size");
            helper.assertValueEqual(machine.getInternalInventory().extractItem(9, Integer.MAX_VALUE, false).getCount(), 32, "The remaining 32 outputs must still be extractable");
        }).thenSucceed();
    }

    private static AEBaseInvBlockEntity placeMachine(GameTestHelper helper, String blockId) {
        helper.setBlock(new BlockPos(0, 1, 1), BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("ae2", "creative_energy_cell")));
        helper.setBlock(MACHINE_POS, BuiltInRegistries.BLOCK.get(ResourceLocation.parse(blockId)));
        AEBaseInvBlockEntity machine = helper.getBlockEntity(MACHINE_POS);
        IUpgradeableObject upgraded = (IUpgradeableObject) machine;
        helper.assertTrue(upgraded.getUpgrades().insertItem(0, DEItems.MULTIPLICATION_CARD.toStack(), false).isEmpty(), "Supported machine must accept the card");
        helper.assertTrue(!upgraded.getUpgrades().insertItem(1, DEItems.MULTIPLICATION_CARD.toStack(), false).isEmpty(), "Every machine must reject a second card");
        return machine;
    }

    private static ItemStack[] crystalInputs() {
        return new ItemStack[] {AEItems.CERTUS_QUARTZ_CRYSTAL_CHARGED.stack(4), new ItemStack(Items.REDSTONE, 4), new ItemStack(Items.QUARTZ, 4)};
    }

    private static boolean skipAbsentMod(GameTestHelper helper, String modId) {
        if (ModList.get().isLoaded(modId)) return false;
        helper.succeed();
        return true;
    }
}
