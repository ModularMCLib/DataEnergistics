package com.fish_dan_.data_energistics.integration.crafting.packaged.extendedcrafting;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.crafting.packaged.execution.PackagedOperationState;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import com.blakebr0.extendedcrafting.tileentity.BasicTableTileEntity;

import java.math.BigInteger;
import java.util.List;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class TableCrafterGameTest {

    @TestHolder("packaged_extended_table_handles_implicit_tier_trimmed_grid_and_foreign_insertion")
    @EmptyTemplate("9")
    @GameTest(template = "empty_9x9")
    public static void tableUsesNativeResultAndKeepsForeignItems(GameTestHelper helper) {
        var pos = helper.absolutePos(new BlockPos(4, 2, 4));
        var level = helper.getLevel();
        level.setBlockAndUpdate(pos, BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("extendedcrafting", "basic_table")).defaultBlockState());
        var tile = (BasicTableTileEntity) level.getBlockEntity(pos);
        var adapter = new TableCrafterAdapter(1);
        var recipeId = Data_Energistics.id("packaged/extendedcrafting/table_test");
        var inputs = new KeyCounter();
        inputs.add(AEItemKey.of(Items.MILK_BUCKET), 1);
        inputs.add(AEItemKey.of(Items.AMETHYST_SHARD), 1);
        var counters = new KeyCounter[] { inputs };
        var pattern = new OutputPattern(List.of(new GenericStack(AEItemKey.of(Items.DIAMOND), 1), new GenericStack(AEItemKey.of(Items.BUCKET), 1)));
        var preparation = adapter.prepare(level, pos, Direction.UP, recipeId, pattern, counters);
        helper.assertTrue(preparation != null, "A recipe without an explicit tier must admit its trimmed grid");
        var operation = new PackagedOperationState(adapter.id(), recipeId, pos, Direction.UP, preparation, counters);
        tile.getInventory().setStackInSlot(0, new ItemStack(Items.MILK_BUCKET));
        operation.advance(level, adapter);
        helper.assertValueEqual(operation.available(AEItemKey.of(Items.MILK_BUCKET)), BigInteger.ONE, "Foreign identical input must not be combined with owned inputs");
        helper.assertValueEqual(tile.getInventory().getStackInSlot(0).getCount(), 1, "Foreign stack must remain unchanged");
        tile.getInventory().setStackInSlot(0, ItemStack.EMPTY);
        operation.advance(level, adapter);
        operation.advance(level, adapter);
        helper.assertTrue(operation.failure() == null, "Native table operation must succeed: " + operation.failure());
        var state = operation.save(level.registryAccess());
        helper.assertTrue(state.getBoolean("complete"), "Native result-slot take must complete the operation");
        var outputs = state.getList("outputs", Tag.TAG_COMPOUND);
        helper.assertValueEqual(outputs.size(), 2, "Result and native bucket remainder must both be returned");
        for (int i = 0; i < outputs.size(); i++) {
            var key = AEKey.fromTagGeneric(level.registryAccess(), outputs.getCompound(i).getCompound("key"));
            helper.assertTrue(key.equals(AEItemKey.of(Items.BUCKET)) || key.equals(AEItemKey.of(Items.DIAMOND)), "Only actual recipe outputs may be returned");
            helper.assertValueEqual(outputs.getCompound(i).getString("amount"), "1", "Each physical output must be counted once");
        }
        for (int slot = 0; slot < tile.getInventory().getSlots(); slot++) helper.assertTrue(tile.getInventory().getStackInSlot(slot).isEmpty(), "Returned remainder must no longer exist in the table");
        helper.succeed();
    }

    private record OutputPattern(List<GenericStack> getOutputs) implements IPatternDetails {

        public AEItemKey getDefinition() {
            return AEItemKey.of(Items.PAPER);
        }

        public IInput[] getInputs() {
            return new IInput[0];
        }
    }
}
