package com.fish_dan_.data_energistics.integration.crafting.packaged.avaritia;

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
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import committee.nova.mods.avaritia.init.registry.enums.ModCraftTier;

import java.util.List;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class AvaritiaPackagedCraftingGameTest {

    private static final ResourceLocation TIER_RECIPE = Data_Energistics.id(
            "packaged/avaritia/tier_remainder_test");
    private static final ResourceLocation SMITHING_RECIPE = Data_Energistics.id(
            "packaged/avaritia/extreme_smithing_test");

    private AvaritiaPackagedCraftingGameTest() {}

    @TestHolder("packaged_avaritia_tier_table_uses_native_remainder_menu")
    @EmptyTemplate("9")
    @GameTest(template = "empty_9x9", timeoutTicks = 100)
    public static void tierTableUsesNativeRemainderMenu(GameTestHelper helper) {
        BlockPos position = helper.absolutePos(new BlockPos(5, 4, 5));
        helper.getLevel().setBlockAndUpdate(position, block("sculk_crafting_table").defaultBlockState());
        var adapter = new TierCraftingAdapter(ModCraftTier.SCULK);
        var supplied = new KeyCounter();
        supplied.add(AEItemKey.of(Items.MILK_BUCKET), 1);
        var pattern = pattern(new GenericStack(AEItemKey.of(Items.IRON_INGOT), 1),
                new GenericStack(AEItemKey.of(Items.BUCKET), 1));
        var prepared = adapter.prepare(helper.getLevel(), position, Direction.UP, TIER_RECIPE, pattern,
                new KeyCounter[] { supplied });
        helper.assertTrue(prepared != null, "Tier table recipe with a bucket remainder must prepare");
        var operation = new PackagedOperationState(adapter.id(), TIER_RECIPE, position, Direction.UP,
                prepared, new KeyCounter[] { supplied });
        operation.advance(helper.getLevel(), adapter);
        helper.onEachTick(() -> operation.advance(helper.getLevel(), adapter));
        helper.succeedWhen(() -> {
            helper.assertTrue(operation.failure() == null, "Tier table operation must use the native result slot: " + operation.failure());
            helper.assertTrue(operation.save(helper.getLevel().registryAccess()).getBoolean("complete"),
                    "Tier table operation must complete");
            var outputs = operation.save(helper.getLevel().registryAccess()).getList("outputs", Tag.TAG_COMPOUND);
            helper.assertValueEqual(outputs.size(), 2, "The native result and bucket remainder must each return once");
        });
    }

    @TestHolder("packaged_avaritia_extreme_smithing_uses_native_result_slot")
    @EmptyTemplate("9")
    @GameTest(template = "empty_9x9", timeoutTicks = 100)
    public static void extremeSmithingUsesNativeResultSlot(GameTestHelper helper) {
        BlockPos position = helper.absolutePos(new BlockPos(5, 4, 5));
        helper.getLevel().setBlockAndUpdate(position, block("extreme_smithing_table").defaultBlockState());
        var adapter = new ExtremeSmithingAdapter();
        var supplied = new KeyCounter();
        supplied.add(AEItemKey.of(Items.PAPER), 1);
        supplied.add(AEItemKey.of(Items.IRON_INGOT), 1);
        supplied.add(AEItemKey.of(Items.GOLD_INGOT), 3);
        var pattern = pattern(new GenericStack(AEItemKey.of(Items.DIAMOND), 1));
        var prepared = adapter.prepare(helper.getLevel(), position, Direction.UP, SMITHING_RECIPE, pattern,
                new KeyCounter[] { supplied });
        helper.assertTrue(prepared != null, "Extreme smithing recipe must prepare five native input slots");
        var operation = new PackagedOperationState(adapter.id(), SMITHING_RECIPE, position, Direction.UP,
                prepared, new KeyCounter[] { supplied });
        operation.advance(helper.getLevel(), adapter);
        helper.onEachTick(() -> operation.advance(helper.getLevel(), adapter));
        helper.succeedWhen(() -> {
            helper.assertTrue(operation.failure() == null, "Extreme smithing native result slot must not fail: " + operation.failure());
            helper.assertTrue(operation.save(helper.getLevel().registryAccess()).getBoolean("complete"),
                    "Extreme smithing operation must complete");
            var outputs = operation.save(helper.getLevel().registryAccess()).getList("outputs", Tag.TAG_COMPOUND);
            helper.assertValueEqual(outputs.size(), 1, "Extreme smithing must return one native result");
        });
    }

    private static Block block(String path) {
        return BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("avaritia", path));
    }

    private static IPatternDetails pattern(GenericStack... outputs) {
        return new OutputPattern(List.of(outputs));
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
