package com.fish_dan_.data_energistics.integration.crafting.packaged.actuallyadditions;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.crafting.packaged.execution.PackagedOperationState;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
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
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import de.ellpeck.actuallyadditions.api.lens.LensConversion;
import de.ellpeck.actuallyadditions.mod.blocks.ActuallyBlocks;
import de.ellpeck.actuallyadditions.mod.blocks.BlockAtomicReconstructor;
import de.ellpeck.actuallyadditions.mod.crafting.ColorChangeRecipe;
import de.ellpeck.actuallyadditions.mod.crafting.LaserRecipe;
import de.ellpeck.actuallyadditions.mod.items.ActuallyItems;
import de.ellpeck.actuallyadditions.mod.items.lens.LensColor;
import de.ellpeck.actuallyadditions.mod.tile.TileEntityAtomicReconstructor;

import java.math.BigInteger;
import java.util.List;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class AtomicReconstructorGameTest {

    private AtomicReconstructorGameTest() {}

    @TestHolder("packaged_actually_additions_atomic_color_lens_runs_native_conversion")
    @EmptyTemplate("9")
    @GameTest(template = "empty_9x9")
    public static void colorLens(GameTestHelper helper) {
        BlockPos position = helper.absolutePos(new BlockPos(4, 2, 4));
        TileEntityAtomicReconstructor machine = place(helper, position, ActuallyItems.LENS_OF_COLOR.get().getDefaultInstance());
        ResourceLocation recipeId = ResourceLocation.fromNamespaceAndPath("actuallyadditions", "colorchange/white_wool");
        AEItemKey inputKey = AEItemKey.of(Items.BLACK_WOOL);
        AEItemKey outputKey = AEItemKey.of(Items.WHITE_WOOL);
        var input = new KeyCounter();
        input.add(inputKey, 2);
        var pattern = new OutputPattern(List.of(new GenericStack(outputKey, 2)));
        var adapter = new AtomicReconstructorAdapter();
        helper.assertTrue(machine.getLens() instanceof LensColor, "Color test must install the native color lens");
        var holder = helper.getLevel().getRecipeManager().byKey(recipeId);
        helper.assertTrue(holder.isPresent() && holder.get().value() instanceof ColorChangeRecipe,
                "Color recipe must be present as an AA ColorChangeRecipe");
        helper.assertTrue(ColorChangeRecipe.getRecipeForStack(inputKey.toStack()).map(selected -> selected.id().equals(recipeId)).orElse(false),
                "AA color recipe cache must select the requested recipe");
        var preparation = adapter.prepare(helper.getLevel(), position, Direction.UP, recipeId, pattern, new KeyCounter[] { input });
        helper.assertTrue(preparation != null, "Color lens must admit the actual color-change recipe");
        var operation = new PackagedOperationState(adapter.id(), recipeId, position, Direction.UP, preparation, new KeyCounter[] { input },
                adapter.occupiedPositions(helper.getLevel(), position, preparation));
        machine.storage.setEnergyStored(0);
        operation.advance(helper.getLevel(), adapter);
        helper.assertValueEqual(operation.available(inputKey), BigInteger.valueOf(2), "An unpowered color lens must keep the provider's materials");
        machine.storage.setEnergyStored(machine.storage.getMaxEnergyStored());
        operation.advance(helper.getLevel(), adapter);
        operation.advance(helper.getLevel(), adapter);
        assertOutput(helper, operation, outputKey, 2);
        helper.assertTrue(machine.getEnergy() < machine.storage.getMaxEnergyStored(), "Native color lens must consume energy");
        helper.succeed();
    }

    @TestHolder("packaged_actually_additions_atomic_conversion_lens_runs_native_laser_recipe")
    @EmptyTemplate("9")
    @GameTest(template = "empty_9x9")
    public static void conversionLens(GameTestHelper helper) {
        BlockPos position = helper.absolutePos(new BlockPos(4, 2, 4));
        TileEntityAtomicReconstructor machine = place(helper, position, ItemStack.EMPTY);
        ResourceLocation recipeId = ResourceLocation.fromNamespaceAndPath("actuallyadditions", "laser/crystalize_enori_crystal");
        AEItemKey inputKey = AEItemKey.of(Items.IRON_INGOT);
        AEItemKey outputKey = AEItemKey.of(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("actuallyadditions", "enori_crystal")));
        var input = new KeyCounter();
        input.add(inputKey, 1);
        var pattern = new OutputPattern(List.of(new GenericStack(outputKey, 1)));
        var adapter = new AtomicReconstructorAdapter();
        helper.assertTrue(machine.getLens() instanceof LensConversion, "Conversion test must use the native default conversion lens");
        var holder = helper.getLevel().getRecipeManager().byKey(recipeId);
        helper.assertTrue(holder.isPresent() && holder.get().value() instanceof LaserRecipe,
                "Conversion recipe must be present as an AA LaserRecipe");
        helper.assertTrue(LaserRecipe.getRecipeForStack(inputKey.toStack()).map(selected -> selected.id().equals(recipeId)).orElse(false),
                "AA laser recipe cache must select the requested recipe");
        var preparation = adapter.prepare(helper.getLevel(), position, Direction.UP, recipeId, pattern, new KeyCounter[] { input });
        helper.assertTrue(preparation != null, "Default conversion lens must admit the actual laser recipe");
        var operation = new PackagedOperationState(adapter.id(), recipeId, position, Direction.UP, preparation, new KeyCounter[] { input },
                adapter.occupiedPositions(helper.getLevel(), position, preparation));
        machine.inv.setStackInSlot(0, ActuallyItems.LENS_OF_COLOR.get().getDefaultInstance());
        operation.advance(helper.getLevel(), adapter);
        helper.assertValueEqual(operation.available(inputKey), BigInteger.ONE, "A changed lens must not consume admitted conversion inputs");
        helper.assertTrue(operation.failure() == null, "A temporarily changed lens must leave the job pending");
        machine.inv.setStackInSlot(0, ItemStack.EMPTY);
        operation.advance(helper.getLevel(), adapter);
        assertOutput(helper, operation, outputKey, 1);
        helper.assertTrue(machine.getEnergy() < machine.storage.getMaxEnergyStored(), "Native conversion lens must consume energy");
        helper.succeed();
    }

    private static TileEntityAtomicReconstructor place(GameTestHelper helper, BlockPos position, ItemStack lens) {
        var state = ActuallyBlocks.ATOMIC_RECONSTRUCTOR.get().defaultBlockState()
                .setValue(BlockAtomicReconstructor.FACING, Direction.UP);
        helper.getLevel().setBlockAndUpdate(position, state);
        helper.getLevel().setBlockAndUpdate(position.relative(Direction.UP), Blocks.AIR.defaultBlockState());
        var machine = (TileEntityAtomicReconstructor) helper.getLevel().getBlockEntity(position);
        helper.assertTrue(machine != null, "Atomic Reconstructor block must create its native tile");
        machine.inv.setStackInSlot(0, lens);
        machine.storage.setEnergyStored(machine.storage.getMaxEnergyStored());
        for (int distance = 1; distance <= machine.getLens().getDistance(); distance++) {
            var beamPos = position.relative(machine.getOrientation(), distance);
            for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
                helper.getLevel().setBlockAndUpdate(beamPos.offset(x, 0, z), Blocks.AIR.defaultBlockState());
            }
            helper.assertTrue(helper.getLevel().isLoaded(beamPos), "Beam chunk must be loaded at " + beamPos);
            helper.assertTrue(helper.getLevel().getBlockState(beamPos).isAir(), "Beam blocked at " + beamPos + " by " + helper.getLevel().getBlockState(beamPos));
        }
        return machine;
    }

    private static void assertOutput(GameTestHelper helper, PackagedOperationState operation, AEItemKey key, long expected) {
        var outputs = operation.save(helper.getLevel().registryAccess()).getList("outputs", Tag.TAG_COMPOUND);
        long amount = 0;
        for (int index = 0; index < outputs.size(); index++) {
            var entry = outputs.getCompound(index);
            if (key.equals(AEItemKey.fromTagGeneric(helper.getLevel().registryAccess(), entry.getCompound("key")))) {
                amount += Long.parseLong(entry.getString("amount"));
            }
        }
        helper.assertValueEqual(amount, expected, "Native Atomic Reconstructor output must be returned exactly once");
        helper.assertTrue(operation.failure() == null, "Atomic Reconstructor operation must not fail: " + operation.failure());
        helper.assertTrue(operation.save(helper.getLevel().registryAccess()).getBoolean("complete"), "Atomic Reconstructor operation must complete");
    }

    private record OutputPattern(List<GenericStack> getOutputs) implements IPatternDetails {

        @Override
        public AEItemKey getDefinition() {
            return AEItemKey.of(Items.PAPER);
        }

        @Override
        public IInput[] getInputs() {
            return new IInput[0];
        }
    }
}
