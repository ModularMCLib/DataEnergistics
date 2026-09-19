package com.fish_dan_.data_energistics.integration.crafting.packaged.draconicevolution;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.crafting.packaged.execution.PackagedOperationState;
import com.fish_dan_.data_energistics.integration.energy.brandonscore.BrandonsCoreEnergyIntegration;

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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import com.brandon3055.brandonscore.api.TechLevel;
import com.brandon3055.draconicevolution.api.DraconicAPI;
import com.brandon3055.draconicevolution.api.crafting.IFusionRecipe;
import com.brandon3055.draconicevolution.blocks.tileentity.TileFusionCraftingCore;
import com.brandon3055.draconicevolution.blocks.tileentity.TileFusionCraftingInjector;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jspecify.annotations.Nullable;

import java.util.List;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class DraconicFusionGameTest {

    private static final ResourceLocation RECIPE = Data_Energistics.id(
            "packaged/draconicevolution/awakened_core_test");
    private static final ResourceLocation MULTI_COUNT_RECIPE = Data_Energistics.id(
            "packaged/draconicevolution/multi_count_remainder_test");

    private DraconicFusionGameTest() {}

    @TestHolder("packaged_draconic_fusion_uses_real_energy_and_returns_native_output")
    @EmptyTemplate("50")
    // Wyvern injectors need 220 charging ticks plus 220 crafting ticks, before transition/collection overhead.
    @GameTest(template = "empty_50x32x50", timeoutTicks = 600)
    public static void fusionConsumesEnergyAndReturnsNativeOutput(GameTestHelper helper) {
        BlockPos corePosition = helper.absolutePos(new BlockPos(7, 4, 7));
        helper.getLevel().setBlockAndUpdate(corePosition, block("crafting_core").defaultBlockState());
        var injectors = placeInjectors(helper, corePosition);
        TileFusionCraftingCore core = requireCore(helper, corePosition);
        var adapter = new DraconicFusionAdapter();
        var inputs = inputs(Items.NETHERITE_SCRAP);
        var pattern = new OutputPattern(List.of(new GenericStack(item("awakened_core"), 1)));
        var prepared = adapter.prepare(helper.getLevel(), corePosition, Direction.UP, RECIPE, pattern, inputs);
        helper.assertTrue(prepared != null, "An exact native fusion recipe must prepare without changing the world");
        helper.assertTrue(core.getCatalystStack().isEmpty() && injectors.stream()
                .allMatch(injector -> injector.getInjectorStack().isEmpty()),
                "Preparation must leave the core and injectors untouched");

        var operation = new PackagedOperationState(adapter.id(), RECIPE, corePosition, Direction.UP,
                requirePrepared(prepared), inputs);
        operation.advance(helper.getLevel(), adapter);
        helper.assertTrue(operation.failure() == null && core.isCrafting(),
                "Dispatch must start Draconic Evolution's real state machine: " + nativeState(core, operation));
        helper.assertTrue(core.getActiveRecipe() != null && core.getActiveRecipe().id().equals(RECIPE),
                "The core must select the exact persisted recipe ID");

        final long[] deliveredEnergy = { 0 };
        helper.onEachTick(() -> {
            operation.advance(helper.getLevel(), adapter);
            for (var injector : injectors) {
                var storage = BrandonsCoreEnergyIntegration.findEnergyStorage(
                        helper.getLevel(), injector.getBlockPos(), null);
                if (storage != null && BrandonsCoreEnergyIntegration.supports(storage)) {
                    long inserted = BrandonsCoreEnergyIntegration.insert(storage, 1_000_000L, false);
                    deliveredEnergy[0] = Math.addExact(deliveredEnergy[0], inserted);
                }
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(operation.failure() == null, "The real fusion operation must not fail: " + operation.failure());
            helper.assertTrue(operation.save(helper.getLevel().registryAccess()).getBoolean("complete"),
                    "Operation completion must follow the native fusion result: " + nativeState(core, operation) + ", energy=" + deliveredEnergy[0]);
            helper.assertTrue(deliveredEnergy[0] >= 1_000_000L,
                    "The native injectors must receive the recipe's real OP cost");
            helper.assertTrue(core.getCatalystStack().isEmpty() && core.getOutputStack().isEmpty(),
                    "The consumed catalyst and harvested output must leave the core empty");
            helper.assertTrue(injectors.stream().allMatch(injector -> injector.getInjectorStack().isEmpty()),
                    "Consumed native ingredients must leave every selected injector empty");
            helper.assertTrue(injectors.stream().allMatch(injector -> injector.getInjectorEnergy() == 0),
                    "The real fusion craft must consume the supplied injector energy");
            var outputs = operation.save(helper.getLevel().registryAccess()).getList("outputs", Tag.TAG_COMPOUND);
            helper.assertValueEqual(outputs.size(), 1, "The return ledger must contain exactly the native result");
            var output = outputs.getCompound(0);
            helper.assertValueEqual(AEKey.fromTagGeneric(helper.getLevel().registryAccess(), output.getCompound("key")),
                    item("awakened_core"), "The harvested result must retain its actual item identity");
            helper.assertValueEqual(output.getString("amount"), "1", "The return ledger must contain one actual result");
        });
    }

    @TestHolder("packaged_draconic_fusion_rejects_shadowed_recipe_before_material_transfer")
    @EmptyTemplate("50")
    @GameTest(template = "empty_50x32x50")
    public static void conflictingRecipeRejectsBeforeChangingMaterials(GameTestHelper helper) {
        BlockPos position = helper.absolutePos(new BlockPos(7, 4, 7));
        var level = helper.getLevel();
        level.setBlockAndUpdate(position, block("crafting_core").defaultBlockState());
        var injectors = placeInjectors(helper, position);
        var core = requireCore(helper, position);
        var supplied = inputs(Items.NETHER_STAR);
        var pattern = new OutputPattern(List.of(new GenericStack(item("awakened_core"), 1)));
        var nativeId = id("components/awakened_core");
        var duplicateId = Data_Energistics.id("packaged/draconicevolution/conflicting_awakened_core_test");
        var recipe = (IFusionRecipe) level.getRecipeManager().byKey(nativeId).orElseThrow().value();
        var plan = FusionRecipePlan.prepare(level, recipe, TechLevel.WYVERN, pattern, supplied);
        helper.assertTrue(plan != null, "Both competing recipes must accept the physical ingredients");
        var snapshot = FusionSnapshot.inventory(plan.catalyst(), plan.injectors(), TechLevel.WYVERN);
        var chosen = level.getRecipeManager().getRecipeFor(DraconicAPI.FUSION_RECIPE_TYPE.get(), snapshot, level).orElseThrow();
        var rejectedId = chosen.id().equals(duplicateId) ? nativeId : duplicateId;
        var adapter = new DraconicFusionAdapter();
        helper.assertTrue(adapter.prepare(level, position, Direction.UP, rejectedId, pattern, supplied) == null,
                "A recipe shadowed by native selection must be rejected before admission");
        helper.assertTrue(core.getCatalystStack().isEmpty() && injectors.stream().allMatch(injector -> injector.getInjectorStack().isEmpty()),
                "Rejected admission must leave the physical inventories untouched");
        helper.assertValueEqual(supplied[0].get(AEItemKey.of(Items.NETHER_STAR)), 1L, "Rejected admission must retain the catalyst");
        helper.assertValueEqual(supplied[0].get(item("wyvern_core")), 4L, "Rejected admission must retain the injector inputs");
        helper.succeed();
    }

    @TestHolder("packaged_draconic_fusion_supports_multi_count_remainder")
    @EmptyTemplate("50")
    @GameTest(template = "empty_50x32x50", timeoutTicks = 1200)
    public static void multiCountIngredientReturnsEveryNativeRemainder(GameTestHelper helper) {
        BlockPos corePosition = helper.absolutePos(new BlockPos(7, 4, 7));
        helper.getLevel().setBlockAndUpdate(corePosition, block("crafting_core").defaultBlockState());
        var injectors = placeInjectors(helper, corePosition);
        TileFusionCraftingCore core = requireCore(helper, corePosition);
        var adapter = new DraconicFusionAdapter();
        var supplied = new KeyCounter();
        supplied.add(AEItemKey.of(Items.NETHERITE_SCRAP), 2);
        supplied.add(AEItemKey.of(Items.HONEY_BOTTLE), 4);
        var pattern = new OutputPattern(List.of(
                new GenericStack(AEItemKey.of(Items.DIAMOND), 2),
                new GenericStack(AEItemKey.of(Items.GLASS_BOTTLE), 4)));
        var prepared = adapter.prepare(helper.getLevel(), corePosition, Direction.UP, MULTI_COUNT_RECIPE,
                pattern, new KeyCounter[] { supplied });
        helper.assertTrue(prepared != null, "A StackIngredient count greater than one must prepare");
        var operation = new PackagedOperationState(adapter.id(), MULTI_COUNT_RECIPE, corePosition, Direction.UP,
                requirePrepared(prepared), new KeyCounter[] { supplied });
        operation.advance(helper.getLevel(), adapter);
        helper.onEachTick(() -> {
            operation.advance(helper.getLevel(), adapter);
            for (var injector : injectors) {
                var storage = BrandonsCoreEnergyIntegration.findEnergyStorage(
                        helper.getLevel(), injector.getBlockPos(), null);
                if (storage != null && BrandonsCoreEnergyIntegration.supports(storage)) {
                    BrandonsCoreEnergyIntegration.insert(storage, 1_000_000L, false);
                }
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(operation.failure() == null, "Multi-count fusion must not fail: " + operation.failure());
            helper.assertTrue(operation.save(helper.getLevel().registryAccess()).getBoolean("complete"),
                    "Multi-count fusion must complete through the native state machine");
            helper.assertTrue(injectors.stream().allMatch(injector -> injector.getInjectorStack().isEmpty()),
                    "The multi-count ingredient must be fully consumed");
            var outputs = operation.save(helper.getLevel().registryAccess()).getList("outputs", Tag.TAG_COMPOUND);
            helper.assertValueEqual(outputs.size(), 2, "The result and every native remainder must be returned");
            boolean diamond = false;
            boolean bottles = false;
            for (int index = 0; index < outputs.size(); index++) {
                CompoundTag output = outputs.getCompound(index);
                AEKey key = AEKey.fromTagGeneric(helper.getLevel().registryAccess(), output.getCompound("key"));
                if (key.equals(AEItemKey.of(Items.DIAMOND)) && output.getString("amount").equals("2")) diamond = true;
                if (key.equals(AEItemKey.of(Items.GLASS_BOTTLE)) && output.getString("amount").equals("4")) bottles = true;
            }
            helper.assertTrue(diamond, "The native result must be returned");
            helper.assertTrue(bottles, "Every consumed honey bottle must return its glass bottle");
        });
    }

    @TestHolder("packaged_draconic_fusion_supports_multi_count_without_remainder")
    @EmptyTemplate("50")
    @GameTest(template = "empty_50x32x50", timeoutTicks = 600)
    public static void multiCountIngredientConsumesExactBatch(GameTestHelper helper) {
        BlockPos corePosition = helper.absolutePos(new BlockPos(7, 4, 7));
        helper.getLevel().setBlockAndUpdate(corePosition, block("crafting_core").defaultBlockState());
        var injectors = placeInjectors(helper, corePosition);
        var adapter = new DraconicFusionAdapter();
        var supplied = new KeyCounter();
        supplied.add(AEItemKey.of(Items.NETHERITE_SCRAP), 1);
        supplied.add(AEItemKey.of(Items.IRON_INGOT), 2);
        var pattern = new OutputPattern(List.of(new GenericStack(AEItemKey.of(Items.DIAMOND), 1)));
        var prepared = adapter.prepare(helper.getLevel(), corePosition, Direction.UP,
                Data_Energistics.id("packaged/draconicevolution/multi_count_plain_test"), pattern,
                new KeyCounter[] { supplied });
        helper.assertTrue(prepared != null, "A plain StackIngredient count greater than one must prepare");
        var operation = new PackagedOperationState(adapter.id(),
                Data_Energistics.id("packaged/draconicevolution/multi_count_plain_test"), corePosition, Direction.UP,
                requirePrepared(prepared), new KeyCounter[] { supplied });
        operation.advance(helper.getLevel(), adapter);
        helper.onEachTick(() -> {
            operation.advance(helper.getLevel(), adapter);
            chargeInjectors(helper, injectors);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(operation.failure() == null, "Plain multi-count fusion must not fail: " + operation.failure());
            helper.assertTrue(operation.save(helper.getLevel().registryAccess()).getBoolean("complete"),
                    "Plain multi-count fusion must complete");
            helper.assertTrue(injectors.stream().allMatch(injector -> injector.getInjectorStack().isEmpty()),
                    "A consumed multi-count batch must leave the injector empty");
        });
    }

    @TestHolder("packaged_draconic_fusion_retains_multi_count_ingredient")
    @EmptyTemplate("50")
    @GameTest(template = "empty_50x32x50", timeoutTicks = 600)
    public static void retainedMultiCountIngredientReturnsWholeBatch(GameTestHelper helper) {
        BlockPos corePosition = helper.absolutePos(new BlockPos(7, 4, 7));
        helper.getLevel().setBlockAndUpdate(corePosition, block("crafting_core").defaultBlockState());
        var injectors = placeInjectors(helper, corePosition);
        var adapter = new DraconicFusionAdapter();
        var supplied = new KeyCounter();
        supplied.add(AEItemKey.of(Items.NETHERITE_SCRAP), 1);
        supplied.add(AEItemKey.of(Items.GOLD_INGOT), 2);
        var pattern = new OutputPattern(List.of(
                new GenericStack(AEItemKey.of(Items.DIAMOND), 1),
                new GenericStack(AEItemKey.of(Items.GOLD_INGOT), 2)));
        ResourceLocation recipe = Data_Energistics.id("packaged/draconicevolution/multi_count_retained_test");
        var prepared = adapter.prepare(helper.getLevel(), corePosition, Direction.UP, recipe, pattern,
                new KeyCounter[] { supplied });
        helper.assertTrue(prepared != null, "A retained StackIngredient count greater than one must prepare");
        var operation = new PackagedOperationState(adapter.id(), recipe, corePosition, Direction.UP,
                requirePrepared(prepared), new KeyCounter[] { supplied });
        operation.advance(helper.getLevel(), adapter);
        helper.onEachTick(() -> {
            operation.advance(helper.getLevel(), adapter);
            chargeInjectors(helper, injectors);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(operation.failure() == null, "Retained multi-count fusion must not fail: " + operation.failure());
            helper.assertTrue(operation.save(helper.getLevel().registryAccess()).getBoolean("complete"),
                    "Retained multi-count fusion must complete");
            helper.assertTrue(injectors.stream().allMatch(injector -> injector.getInjectorStack().isEmpty()),
                    "The retained batch must be returned by the operation");
            var outputs = operation.save(helper.getLevel().registryAccess()).getList("outputs", Tag.TAG_COMPOUND);
            helper.assertTrue(outputs.size() == 2, "Retained input and result must both be returned");
        });
    }

    private static void chargeInjectors(GameTestHelper helper, List<TileFusionCraftingInjector> injectors) {
        for (var injector : injectors) {
            var storage = BrandonsCoreEnergyIntegration.findEnergyStorage(helper.getLevel(), injector.getBlockPos(), null);
            if (storage != null && BrandonsCoreEnergyIntegration.supports(storage)) {
                BrandonsCoreEnergyIntegration.insert(storage, 1_000_000L, false);
            }
        }
    }

    private static List<TileFusionCraftingInjector> placeInjectors(GameTestHelper helper, BlockPos core) {
        var positions = List.of(
                core.offset(3, 0, 0), core.offset(-3, 0, 0),
                core.offset(0, 0, 3), core.offset(0, 0, -3),
                core.offset(3, 1, 0), core.offset(-3, 1, 0),
                core.offset(0, 1, 3), core.offset(0, 1, -3));
        var injectors = new ObjectArrayList<TileFusionCraftingInjector>();
        for (BlockPos position : positions) {
            Direction facing = Direction.getNearest(
                    core.getX() - position.getX(),
                    core.getY() - position.getY(),
                    core.getZ() - position.getZ());
            helper.getLevel().setBlockAndUpdate(position,
                    block("wyvern_crafting_injector").defaultBlockState()
                            .setValue(BlockStateProperties.FACING, facing));
            injectors.add((TileFusionCraftingInjector) helper.getLevel().getBlockEntity(position));
        }
        return List.copyOf(injectors);
    }

    private static KeyCounter[] inputs(Item catalyst) {
        KeyCounter inputs = new KeyCounter();
        inputs.add(AEItemKey.of(catalyst), 1);
        inputs.add(item("wyvern_core"), 4);
        inputs.add(AEItemKey.of(BuiltInRegistries.ITEM.get(
                ResourceLocation.fromNamespaceAndPath("draconicevolution", "awakened_draconium_ingot"))), 4);
        return new KeyCounter[] { inputs };
    }

    private static Block block(String path) {
        return BuiltInRegistries.BLOCK.get(id(path));
    }

    private static String nativeState(TileFusionCraftingCore core, PackagedOperationState operation) {
        var active = core.getActiveRecipe();
        var stacks = new ObjectArrayList<String>();
        for (var injector : core.getInjectors()) {
            stacks.add((injector instanceof TileFusionCraftingInjector tile ? tile.getBlockPos().toString() : "snapshot") + "=" + injector.getInjectorStack().getHoverName().getString() + "x" + injector.getInjectorStack().getCount());
        }
        return "failure=" + operation.failure() + ", crafting=" + core.isCrafting() + ", active=" + (active == null ? "null" : active.id()) + ", state=" + core.getFusionState() + ", injectors=" + core.getInjectors().size() + ", stacks=" + stacks;
    }

    private static TileFusionCraftingCore requireCore(GameTestHelper helper, BlockPos position) {
        if (helper.getLevel().getBlockEntity(position) instanceof TileFusionCraftingCore core) return core;
        throw new IllegalStateException("Missing Draconic fusion core");
    }

    private static CompoundTag requirePrepared(@Nullable CompoundTag prepared) {
        if (prepared == null) throw new IllegalStateException("Expected successful Draconic fusion preparation");
        return prepared;
    }

    private static AEItemKey item(String path) {
        return AEItemKey.of(BuiltInRegistries.ITEM.get(id(path)));
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("draconicevolution", path);
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
