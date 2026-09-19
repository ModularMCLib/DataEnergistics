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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import com.blakebr0.extendedcrafting.tileentity.CraftingCoreTileEntity;
import com.blakebr0.extendedcrafting.tileentity.PedestalTileEntity;
import org.jspecify.annotations.Nullable;

import java.math.BigInteger;
import java.util.List;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class CombinationCraftingGameTest {

    private static final ResourceLocation RECIPE = Data_Energistics.id("packaged/extendedcrafting/combination_test");

    private CombinationCraftingGameTest() {}

    @TestHolder("packaged_extended_crafting_combination_uses_native_core_energy_and_remainders")
    @EmptyTemplate("9")
    @GameTest(template = "empty_9x9", timeoutTicks = 100)
    public static void combinationUsesNativeCore(GameTestHelper helper) {
        BlockPos corePosition = helper.absolutePos(new BlockPos(4, 2, 4));
        helper.getLevel().setBlockAndUpdate(corePosition, block("crafting_core").defaultBlockState());
        var pedestals = placePedestals(helper, corePosition);
        CraftingCoreTileEntity core = requireCore(helper, corePosition);
        var inputs = inputs();
        var pattern = pattern(new ItemStack(Items.NETHERITE_INGOT), new ItemStack(Items.BUCKET));
        var adapter = new CombinationCraftingAdapter();
        CompoundTag preparation = requirePrepared(adapter.prepare(helper.getLevel(), corePosition, Direction.UP, RECIPE,
                pattern, inputs));
        helper.assertTrue(core.getInventory().getStackInSlot(0).isEmpty() && pedestals.stream()
                .allMatch(pedestal -> pedestal.getInventory().getStackInSlot(0).isEmpty()),
                "Preparation must not mutate the native core or pedestals");

        PackagedOperationState[] operation = { new PackagedOperationState(adapter.id(), RECIPE, corePosition,
                Direction.UP, preparation, inputs, adapter.occupiedPositions(helper.getLevel(), corePosition, preparation)) };
        operation[0].advance(helper.getLevel(), adapter);
        helper.assertTrue(operation[0].failure() == null, "The operation must deliver the physical combination inputs");
        // Exercise the real persisted operation boundary after delivery and before native processing completes.
        operation[0] = PackagedOperationState.load(operation[0].save(helper.getLevel().registryAccess()),
                helper.getLevel().registryAccess());
        core.getEnergy().receiveEnergy(100, false);
        helper.assertTrue(operation[0].save(helper.getLevel().registryAccess()).getList("outputs", Tag.TAG_COMPOUND).isEmpty(),
                "No output may be returned before the native core completes");
        helper.onEachTick(() -> {
            if (!operation[0].settled()) operation[0].advance(helper.getLevel(), adapter);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(operation[0].failure() == null, "The native combination operation must not fail: " + operation[0].failure());
            helper.assertTrue(savedComplete(operation[0], helper), "The native core must complete after consuming its real power cost");
            helper.assertValueEqual(core.getEnergy().getEnergyStored(), 0,
                    "The native core must consume the recipe's complete power cost");
            var saved = operation[0].save(helper.getLevel().registryAccess());
            helper.assertValueEqual(saved.getList("outputs", Tag.TAG_COMPOUND).size(), 2,
                    "The persisted return ledger must contain the native result and bucket remainder");
            helper.assertValueEqual(AEKey.fromTagGeneric(helper.getLevel().registryAccess(),
                    saved.getList("outputs", Tag.TAG_COMPOUND).getCompound(0).getCompound("key")), AEItemKey.of(Items.NETHERITE_INGOT),
                    "The output ledger must contain the native core result");
            helper.assertValueEqual(saved.getList("outputs", Tag.TAG_COMPOUND).getCompound(0).getString("amount"), "1",
                    "The persisted output ledger must contain one actual result");
            helper.assertValueEqual(AEKey.fromTagGeneric(helper.getLevel().registryAccess(),
                    saved.getList("outputs", Tag.TAG_COMPOUND).getCompound(1).getCompound("key")),
                    AEItemKey.of(Items.BUCKET),
                    "The native milk bucket remainder must be returned");
            helper.assertValueEqual(operation[0].available(AEItemKey.of(Items.NETHER_STAR)), BigInteger.ZERO,
                    "The persisted state must settle the consumed base input");
            helper.assertTrue(core.getInventory().getStackInSlot(0).isEmpty() && pedestals.stream()
                    .allMatch(pedestal -> pedestal.getInventory().getStackInSlot(0).isEmpty()),
                    "Collecting must drain the real core and pedestal inventories");
            helper.assertTrue(saved.getCompound("progress").getBoolean("delivered"), "Delivery progress must be persisted");
        });
    }

    @TestHolder("packaged_extended_crafting_combination_rejects_before_consuming_inputs")
    @EmptyTemplate("9")
    @GameTest(template = "empty_9x9")
    public static void combinationRejectsWrongEnvelope(GameTestHelper helper) {
        BlockPos corePosition = helper.absolutePos(new BlockPos(4, 2, 4));
        helper.getLevel().setBlockAndUpdate(corePosition, block("crafting_core").defaultBlockState());
        var pedestals = placePedestals(helper, corePosition);
        var supplied = inputs();
        supplied[0].add(AEItemKey.of(Items.GOLD_INGOT), 1);
        var adapter = new CombinationCraftingAdapter();
        helper.assertTrue(adapter.prepare(helper.getLevel(), corePosition, Direction.UP, RECIPE,
                pattern(new ItemStack(Items.NETHERITE_INGOT)), supplied) == null,
                "An extra item must reject the exact combination envelope");
        helper.assertTrue(pedestals.stream().allMatch(pedestal -> pedestal.getInventory().getStackInSlot(0).isEmpty()),
                "Rejected preparation must leave every pedestal untouched");
        helper.assertValueEqual(supplied[0].get(AEItemKey.of(Items.GOLD_INGOT)), 1L,
                "Rejected preparation must retain all provider inputs");
        helper.succeed();
    }

    private static List<PedestalTileEntity> placePedestals(GameTestHelper helper, BlockPos core) {
        var positions = List.of(core.offset(-2, 0, 0), core.offset(2, 0, 0));
        var pedestals = positions.stream().map(position -> {
            helper.getLevel().setBlockAndUpdate(position, block("pedestal").defaultBlockState());
            return (PedestalTileEntity) helper.getLevel().getBlockEntity(position);
        }).toList();
        return pedestals;
    }

    private static KeyCounter[] inputs() {
        KeyCounter counter = new KeyCounter();
        counter.add(AEItemKey.of(Items.NETHER_STAR), 1);
        counter.add(AEItemKey.of(Items.DIAMOND), 1);
        counter.add(AEItemKey.of(Items.MILK_BUCKET), 1);
        return new KeyCounter[] { counter };
    }

    private static IPatternDetails pattern(ItemStack... outputs) {
        return new OutputPattern(List.of(outputs).stream()
                .map(output -> new GenericStack(AEItemKey.of(output), output.getCount())).toList());
    }

    private static Block block(String path) {
        return BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("extendedcrafting", path));
    }

    private static CraftingCoreTileEntity requireCore(GameTestHelper helper, BlockPos position) {
        if (helper.getLevel().getBlockEntity(position) instanceof CraftingCoreTileEntity core) return core;
        throw new IllegalStateException("Missing Extended Crafting core");
    }

    private static CompoundTag requirePrepared(@Nullable CompoundTag preparation) {
        if (preparation != null) return preparation;
        throw new IllegalStateException("Expected a successful combination preparation");
    }

    private static boolean savedComplete(PackagedOperationState operation, GameTestHelper helper) {
        return operation.save(helper.getLevel().registryAccess()).getBoolean("complete");
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
