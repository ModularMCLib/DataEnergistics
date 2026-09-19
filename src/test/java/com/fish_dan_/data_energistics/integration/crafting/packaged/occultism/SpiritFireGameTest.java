package com.fish_dan_.data_energistics.integration.crafting.packaged.occultism;

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
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import com.klikli_dev.occultism.registry.OccultismBlocks;

import java.util.List;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class SpiritFireGameTest {

    private SpiritFireGameTest() {}

    @TestHolder("packaged_occultism_spirit_fire_runs_real_collision_without_collecting_unrelated_drops")
    @EmptyTemplate("9")
    @GameTest(template = "empty_9x9")
    public static void onlyCausalFireOutputsAreCollected(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos fire = helper.absolutePos(new BlockPos(4, 2, 4));
        level.setBlockAndUpdate(fire.below(), Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(fire, OccultismBlocks.SPIRIT_FIRE.get().defaultBlockState());
        ResourceLocation recipeId = ResourceLocation.fromNamespaceAndPath("occultism", "spirit_fire/otherrock");
        AEItemKey result = AEItemKey.of(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("occultism", "otherrock")));
        var unrelated = new ItemEntity(level, fire.getX() + 1.5, fire.getY(), fire.getZ() + 0.5, result.toStack(7));
        level.addFreshEntity(unrelated);
        var adapter = new SpiritFireAdapter();
        var input = new KeyCounter();
        input.add(AEItemKey.of(Items.DIORITE), 2);
        var supplied = new KeyCounter[] { input };
        var pattern = new OutputPattern(List.of(new GenericStack(result, 2)));
        var prepared = adapter.prepare(level, fire, Direction.UP, recipeId, pattern, supplied);
        helper.assertTrue(prepared != null, "A real registered fire recipe must prepare without a block entity");
        var operation = new PackagedOperationState(adapter.id(), recipeId, fire, Direction.UP, prepared, supplied);
        operation.advance(level, adapter);
        operation.advance(level, adapter);
        helper.assertTrue(operation.failure() == null, "Both actual fire collisions must succeed");
        helper.assertTrue(operation.save(level.registryAccess()).getBoolean("complete"), "Two native collisions must settle both inputs");
        helper.assertTrue(!unrelated.isRemoved(), "A pre-existing identical output must remain in the world");
        helper.assertValueEqual(unrelated.getItem().getCount(), 7, "Causal ownership must not steal nearby matching items");
        helper.succeed();
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
