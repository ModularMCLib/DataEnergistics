package com.fish_dan_.data_energistics.common.blockentity.machine;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.data.DEBlocks;
import com.fish_dan_.data_energistics.common.data.DEItems;
import com.fish_dan_.data_energistics.common.item.carrier.OreDataCarrierData;
import com.fish_dan_.data_energistics.common.upgrade.production.MultiplicationCardOutput;
import com.fish_dan_.data_energistics.integration.ae.ae2.key.DataFlowKey;

import appeng.api.stacks.GenericStack;
import appeng.api.upgrades.UpgradeInventories;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class MultiplicationCardMachineGameTest {

    private MultiplicationCardMachineGameTest() {}

    @TestHolder("multiplication_card_external_rounding_and_install_limit")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void externalOutputsRoundDownAndOnlyOneCardFits(GameTestHelper helper) {
        var upgrades = UpgradeInventories.forMachine(AEBlocks.INSCRIBER, 3, () -> {});
        helper.assertTrue(upgrades.insertItem(0, DEItems.MULTIPLICATION_CARD.toStack(), false).isEmpty(), "Inscriber must accept the card");
        helper.assertTrue(!upgrades.insertItem(1, DEItems.MULTIPLICATION_CARD.toStack(), false).isEmpty(), "A second card must be rejected");
        int[] amounts = {0, 1, 2, 3, 6, 31, 32, 64};
        int[] expected = {0, 1, 3, 4, 9, 46, 48, 96};
        for (int index = 0; index < amounts.length; index++) {
            helper.assertValueEqual(MultiplicationCardOutput.boostExternalAmount(amounts[index], upgrades), expected[index], "External output must round down");
        }
        ItemStack original = new ItemStack(Items.DIAMOND, 3);
        helper.assertValueEqual(MultiplicationCardOutput.boostExternalOutput(original, upgrades).getCount(), 4, "The new stack must be boosted");
        helper.assertValueEqual(original.getCount(), 3, "A shared source stack must not change");
        helper.assertValueEqual(MultiplicationCardOutput.boostExternalAmount(1_431_655_765, upgrades), Integer.MAX_VALUE, "Largest fitting item count must not wrap");
        boolean overflowRejected = false;
        try {
            MultiplicationCardOutput.boostExternalAmount(1_431_655_766, upgrades);
        } catch (ArithmeticException expectedOverflow) {
            overflowRejected = true;
        }
        helper.assertTrue(overflowRejected, "Unrepresentable item counts must not silently wrap");
        upgrades.setItemDirect(0, ItemStack.EMPTY);
        helper.assertValueEqual(MultiplicationCardOutput.boostExternalAmount(3, upgrades), 3, "Removing the card restores native amounts");
        helper.succeed();
    }

    @TestHolder("multiplication_card_reassembler_and_async_factory_double_fluid_output")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5", timeoutTicks = 160)
    public static void reassemblerAndFactoryDoubleFluidOutput(GameTestHelper helper) {
        BlockPos reassemblerPos = new BlockPos(1, 1, 1);
        BlockPos factoryPos = new BlockPos(3, 1, 1);
        helper.setBlock(reassemblerPos, DEBlocks.DATA_RIPPER_REASSEMBLER.get());
        helper.setBlock(factoryPos, DEBlocks.DATA_ASYNCHRONOUS_PROCESSING_FACTORY.get());
        helper.setBlock(new BlockPos(2, 1, 1), AEBlocks.CREATIVE_ENERGY_CELL.block());
        DataRipperReassemblerBlockEntity reassembler = helper.getBlockEntity(reassemblerPos);
        DataRipperReassemblerBlockEntity factory = helper.getBlockEntity(factoryPos);
        helper.startSequence().thenWaitUntil(() -> {
            helper.assertTrue(reassembler.isOnline() && factory.isOnline(), "Both machines must join the powered network");
        }).thenExecute(() -> {
            for (DataRipperReassemblerBlockEntity machine : new DataRipperReassemblerBlockEntity[] {reassembler, factory}) {
                machine.getUpgrades().setItemDirect(0, DEItems.MULTIPLICATION_CARD.toStack());
                machine.getInternalInventory().setItemDirect(0, AEItems.ENDER_DUST.stack());
                machine.advanceAdditionalTicks(400);
                helper.assertValueEqual(machine.getFluidOutput(0).getAmount(), 500, "One ender dust must produce 500 mB with the card");
                helper.assertTrue(machine.getInternalInventory().getStackInSlot(0).isEmpty(), "Only one input must be consumed");
                machine.getUpgrades().setItemDirect(0, ItemStack.EMPTY);
                machine.getInternalInventory().setItemDirect(0, AEItems.ENDER_DUST.stack());
                machine.advanceAdditionalTicks(400);
                helper.assertValueEqual(machine.getFluidOutput(0).getAmount(), 750, "Removing the card must add only the native 250 mB");
            }
        }).thenSucceed();
    }

    @TestHolder("multiplication_card_mimetic_pending_outputs_are_not_boosted_again")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void mimeticPendingOutputIsDoubledOnce(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, DEBlocks.DATA_MIMETIC_FIELD.get());
        DataMimeticFieldBlockEntity machine = helper.getBlockEntity(pos);
        ItemStack carrier = DEItems.ORE_DATA_CARRIER.toStack();
        helper.assertTrue(OreDataCarrierData.recordFirstOre(carrier, new ItemStack(Items.IRON_ORE)), "Fixture must record a real ore");
        carrier = OreDataCarrierData.createCompletedCarrier(carrier);
        machine.getInternalInventory().setItemDirect(0, carrier);
        machine.getUpgrades().setItemDirect(0, DEItems.MULTIPLICATION_CARD.toStack());
        machine.setDropRoutingMode(DataExtractorDropRoutingMode.CONTAINER);
        CompoundTag state = new CompoundTag();
        machine.saveAdditional(state, helper.getLevel().registryAccess());
        state.put("key_input", GenericStack.writeTag(helper.getLevel().registryAccess(), new GenericStack(DataFlowKey.of(), 3_200L)));
        machine.loadTag(state, helper.getLevel().registryAccess());
        machine.advanceAdditionalTicks(machine.getWorkMaxProgress());
        CompoundTag pending = new CompoundTag();
        machine.saveAdditional(pending, helper.getLevel().registryAccess());
        long total = 0L;
        for (Tag entry : pending.getList("pending_outputs", Tag.TAG_COMPOUND)) {
            GenericStack stack = GenericStack.readTag(helper.getLevel().registryAccess(), (CompoundTag) entry);
            helper.assertTrue(stack != null, "A pending output must remain serializable");
            total += stack.amount();
        }
        long expectedAmount = machine.getOreOutputRollsPerCycle() * 2L;
        helper.assertValueEqual(total, expectedAmount, "A generated ore batch must be doubled exactly once");
        machine.getUpgrades().setItemDirect(0, ItemStack.EMPTY);
        machine.advanceAdditionalTicks(50);
        helper.setBlock(new BlockPos(2, 1, 1), Blocks.CHEST);
        machine.advanceAdditionalTicks(20);
        ChestBlockEntity chest = helper.getBlockEntity(new BlockPos(2, 1, 1));
        long delivered = 0;
        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
            delivered += chest.getItem(slot).getCount();
        }
        helper.assertValueEqual(delivered, expectedAmount, "Uninstalling and retrying must preserve the already-generated amount");
        helper.succeed();
    }
}
