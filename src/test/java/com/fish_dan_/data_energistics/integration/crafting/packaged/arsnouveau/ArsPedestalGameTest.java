package com.fish_dan_.data_energistics.integration.crafting.packaged.arsnouveau;

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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import com.hollingsworth.arsnouveau.common.block.tile.ArcanePedestalTile;
import com.hollingsworth.arsnouveau.common.block.tile.EnchantingApparatusTile;
import com.hollingsworth.arsnouveau.common.block.tile.ImbuementTile;

import java.util.List;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class ArsPedestalGameTest {

    private ArsPedestalGameTest() {}

    @TestHolder("packaged_ars_apparatus_runs_native_craft_and_rejects_missing_core")
    @EmptyTemplate("9")
    @GameTest(template = "empty_9x9", timeoutTicks = 280)
    public static void apparatusProducesNativeResultAfterItsOwnTicks(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos center = helper.absolutePos(new BlockPos(4, 3, 4));
        level.setBlockAndUpdate(center, BuiltInRegistries.BLOCK.get(id("enchanting_apparatus")).defaultBlockState()
                .setValue(BlockStateProperties.FACING, Direction.UP));
        BlockPos pedestalPos = center.offset(2, 0, 0);
        level.setBlockAndUpdate(pedestalPos, BuiltInRegistries.BLOCK.get(id("arcane_pedestal")).defaultBlockState());
        var apparatus = (EnchantingApparatusTile) level.getBlockEntity(center);
        var adapter = new ArsPedestalAdapter(ArsMachineKind.APPARATUS);
        var recipeId = id("decor_blossom");
        var input = new KeyCounter();
        input.add(AEItemKey.of(Items.SPORE_BLOSSOM), 1);
        input.add(item("conjuration_essence"), 1);
        var supplied = new KeyCounter[] { input };
        var pattern = new OutputPattern(List.of(new GenericStack(item("decor_blossom"), 1)));
        helper.assertTrue(adapter.prepare(level, center, Direction.UP, recipeId, pattern, supplied) == null,
                "Automation must not bypass the arcane core requirement");
        level.setBlockAndUpdate(center.below(), BuiltInRegistries.BLOCK.get(id("arcane_core")).defaultBlockState()
                .setValue(BlockStateProperties.FACING, Direction.UP));
        var prepared = adapter.prepare(level, center, Direction.UP, recipeId, pattern, supplied);
        helper.assertTrue(prepared != null, "A native registered apparatus recipe must prepare");
        helper.assertTrue(apparatus.isEmpty(), "Preparation must leave the center untouched");
        var operation = new PackagedOperationState(adapter.id(), recipeId, center, Direction.UP, prepared, supplied);
        operation.advance(level, adapter);
        helper.assertTrue(operation.failure() == null, "Physical dispatch must not fail");
        helper.assertTrue(apparatus.isCrafting, "The apparatus must enter its native crafting state");
        helper.onEachTick(() -> operation.advance(level, adapter));
        helper.succeedWhen(() -> {
            helper.assertTrue(operation.failure() == null, "Ars operation must stay valid");
            var saved = operation.save(level.registryAccess());
            helper.assertTrue(saved.getBoolean("complete"), "Native apparatus crafting must finish before completion");
            helper.assertTrue(apparatus.isEmpty(), "The actual center output must be harvested");
            var pedestal = (ArcanePedestalTile) level.getBlockEntity(pedestalPos);
            helper.assertTrue(pedestal.isEmpty(), "The native craft must consume the peripheral ingredient");
        });
    }

    @TestHolder("packaged_ars_imbuement_retains_installed_catalysts_and_consumes_source")
    @EmptyTemplate("9")
    @GameTest(template = "empty_9x9", timeoutTicks = 180)
    public static void imbuementPreservesInstalledCatalysts(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos center = helper.absolutePos(new BlockPos(4, 3, 4));
        level.setBlockAndUpdate(center, BuiltInRegistries.BLOCK.get(id("imbuement_chamber")).defaultBlockState());
        var chamber = (ImbuementTile) level.getBlockEntity(center);
        var catalysts = List.of(item("source_gem"), item("air_essence"), AEItemKey.of(Items.DIAMOND));
        var positions = List.of(center.east(), center.west(), center.north());
        for (int index = 0; index < positions.size(); index++) {
            level.setBlockAndUpdate(positions.get(index), BuiltInRegistries.BLOCK.get(id("arcane_pedestal")).defaultBlockState());
            ((ArcanePedestalTile) level.getBlockEntity(positions.get(index))).setItem(0, catalysts.get(index).toStack());
        }
        chamber.setSource(100);
        var adapter = new ArsPedestalAdapter(ArsMachineKind.IMBUEMENT);
        var recipeId = id("imbuement_amplify_arrow");
        var input = new KeyCounter();
        input.add(AEItemKey.of(Items.ARROW), 1);
        var supplied = new KeyCounter[] { input };
        var pattern = new OutputPattern(List.of(new GenericStack(item("amplify_arrow"), 1)));
        var prepared = adapter.prepare(level, center, Direction.UP, recipeId, pattern, supplied);
        helper.assertTrue(prepared != null, "The chamber must accept existing nonconsumable catalysts");
        helper.assertTrue(chamber.isEmpty() && chamber.getSource() == 100, "Recipe preview must not alter the real chamber or Source");
        var operation = new PackagedOperationState(adapter.id(), recipeId, center, Direction.UP, prepared, supplied);
        operation.advance(level, adapter);
        helper.assertTrue(operation.failure() == null, "Native imbuement dispatch must succeed");
        helper.onEachTick(() -> operation.advance(level, adapter));
        helper.succeedWhen(() -> {
            helper.assertTrue(operation.failure() == null, "Imbuement operation must stay valid");
            helper.assertTrue(operation.save(level.registryAccess()).getBoolean("complete"), "Native imbuement must finish");
            helper.assertTrue(chamber.isEmpty(), "Only the actual chamber result must be harvested");
            helper.assertValueEqual(chamber.getSource(), 0, "The real chamber must consume the recipe's 100 Source");
            for (int index = 0; index < positions.size(); index++) {
                var pedestal = (ArcanePedestalTile) level.getBlockEntity(positions.get(index));
                helper.assertValueEqual(AEItemKey.of(pedestal.getStack()), catalysts.get(index), "Installed catalysts must remain owned by their pedestal");
            }
        });
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("ars_nouveau", path);
    }

    private static AEItemKey item(String path) {
        return AEItemKey.of(BuiltInRegistries.ITEM.get(id(path)));
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
