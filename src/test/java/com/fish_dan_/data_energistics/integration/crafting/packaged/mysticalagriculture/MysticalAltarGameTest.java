package com.fish_dan_.data_energistics.integration.crafting.packaged.mysticalagriculture;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.crafting.packaged.PackagedMachineOperation;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import com.blakebr0.cucumber.tileentity.BaseInventoryTileEntity;
import com.blakebr0.mysticalagriculture.init.ModBlocks;
import com.blakebr0.mysticalagriculture.tileentity.AwakeningAltarTileEntity;
import com.blakebr0.mysticalagriculture.tileentity.EssenceVesselTileEntity;
import com.blakebr0.mysticalagriculture.tileentity.InfusionAltarTileEntity;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.jspecify.annotations.Nullable;

import java.math.BigInteger;
import java.util.List;
import java.util.UUID;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class MysticalAltarGameTest {

    private static final ResourceLocation INFUSION = Data_Energistics.id("packaged/mysticalagriculture/infusion_test");
    private static final ResourceLocation AWAKENING = Data_Energistics.id("packaged/mysticalagriculture/awakening_test");

    private MysticalAltarGameTest() {}

    @TestHolder("packaged_ma_infusion_real_ticks_preserve_components_remainders_and_two_cycles")
    @EmptyTemplate("9")
    @GameTest(template = "empty_9x9", timeoutTicks = 260)
    public static void infusionCompletesTwoRealCyclesAcrossProgressReload(GameTestHelper helper) {
        var altar = infusionStructure(helper);
        var adapter = new MysticalAltarAdapter(AltarKind.INFUSION);
        var inputs = infusionInputs(2);
        ItemStack result = namedShard(4);
        var pattern = pattern(result, new ItemStack(Items.BUCKET, 2));
        CompoundTag prepared = adapter.prepare(helper.getLevel(), altar.getBlockPos(), Direction.UP, INFUSION, pattern, inputs);
        helper.assertTrue(prepared != null, "A registered infusion with overlapping alternatives must prepare");
        helper.assertTrue(altar.getInventory().getStackInSlot(0).isEmpty(), "Preparation must not fill the altar");
        var operation = new TestOperation(helper.getLevel(), altar.getBlockPos(), INFUSION, prepared, inputs);
        helper.assertTrue(adapter.advance(operation), "First cycle must enter the real inventories");
        helper.assertTrue(operation.outputs.isEmpty(), "No result may be returned before the altar crafts");
        helper.runAtTickTime(35, () -> operation.progress = operation.progress.copy());
        helper.onEachTick(() -> {
            if (!operation.finished) adapter.advance(operation);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(operation.finished, "Two physical altar crafts must complete");
            helper.assertValueEqual(operation.outputs.get(AEItemKey.of(result)), 4L, "Component-bearing shards must match actual output");
            helper.assertValueEqual(operation.outputs.get(AEItemKey.of(Items.BUCKET)), 2L, "Both real bucket remainders must return");
            helper.assertTrue(operation.inputs.isEmpty(), "Every accepted input must have been delivered exactly once");
            helper.assertTrue(operation.revisions > 0, "Progress updates must request persistence");
            assertEmpty(helper, altar, altar.getPedestalPositions());
        });
    }

    @TestHolder("packaged_ma_awakening_consumes_real_vessel_amounts_with_nonstandard_pedestal_order")
    @EmptyTemplate("9")
    @GameTest(template = "empty_9x9", timeoutTicks = 160)
    public static void awakeningConsumesVesselsThroughItsOwnTick(GameTestHelper helper) {
        var altar = awakeningStructure(helper);
        var adapter = new MysticalAltarAdapter(AltarKind.AWAKENING);
        var inputs = awakeningInputs();
        var pattern = pattern(new ItemStack(Items.NETHERITE_INGOT));
        CompoundTag prepared = adapter.prepare(helper.getLevel(), altar.getBlockPos(), Direction.UP, AWAKENING, pattern, inputs);
        helper.assertTrue(prepared != null, "Four pedestals and four vessels may occupy any of the eight accepted locations");
        var operation = new TestOperation(helper.getLevel(), altar.getBlockPos(), AWAKENING, prepared, inputs);
        helper.assertTrue(adapter.advance(operation), "Awakening inputs must be placed in the real altar and vessels");
        int vessels = 0;
        int[] expectedCounts = { 1, 39, 40, 2 };
        for (BlockPos position : altar.getPedestalPositions()) {
            if (helper.getLevel().getBlockEntity(position) instanceof EssenceVesselTileEntity vessel) {
                helper.assertTrue(vessel.getInventory().getStackInSlot(0).getCount() == expectedCounts[vessels++],
                        "The vessel must hold the physical recipe quantity before processing");
            }
        }
        helper.assertTrue(operation.outputs.isEmpty(), "Awakening must not synthesize immediate output");
        helper.onEachTick(() -> {
            if (!operation.finished) adapter.advance(operation);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(operation.finished, "The awakening machine must complete its real 100-tick cycle");
            helper.assertValueEqual(operation.outputs.get(AEItemKey.of(Items.NETHERITE_INGOT)), 1L, "Only the real altar output returns");
            helper.assertTrue(operation.inputs.isEmpty(), "All essence and pedestal materials must be accounted");
            assertEmpty(helper, altar, altar.getPedestalPositions());
        });
    }

    @TestHolder("packaged_ma_prepare_rejects_wrong_components_amounts_recipe_and_busy_structure")
    @EmptyTemplate("9")
    @GameTest(template = "empty_9x9")
    public static void preparationRejectsUncraftableEnvelopesWithoutChangingWorld(GameTestHelper helper) {
        var altar = infusionStructure(helper);
        var adapter = new MysticalAltarAdapter(AltarKind.INFUSION);
        var inputs = infusionInputs(1);
        helper.assertTrue(adapter.prepare(helper.getLevel(), altar.getBlockPos(), Direction.UP, INFUSION,
                pattern(new ItemStack(Items.AMETHYST_SHARD, 2), new ItemStack(Items.BUCKET)), inputs) == null,
                "Ignoring center-to-result components must reject the pattern");
        helper.assertTrue(adapter.prepare(helper.getLevel(), altar.getBlockPos(), Direction.UP, INFUSION,
                pattern(namedShard(1), new ItemStack(Items.BUCKET)), inputs) == null,
                "Incorrect output count must reject the pattern");
        var pattern = pattern(namedShard(2), new ItemStack(Items.BUCKET));
        helper.assertTrue(adapter.prepare(helper.getLevel(), altar.getBlockPos(), Direction.UP, AWAKENING, pattern, inputs) == null,
                "A registered recipe from the wrong altar type must reject");
        var excessInputs = infusionInputs(1);
        excessInputs[0].add(AEItemKey.of(Items.DIAMOND), 1);
        helper.assertTrue(adapter.prepare(helper.getLevel(), altar.getBlockPos(), Direction.UP, INFUSION, pattern, excessInputs) == null,
                "An incomplete extra cycle must reject before taking any materials");
        BlockPos pedestal = altar.getPedestalPositions().getFirst();
        var tile = (BaseInventoryTileEntity) helper.getLevel().getBlockEntity(pedestal);
        tile.getInventory().setStackInSlot(0, new ItemStack(Items.DIRT));
        helper.assertTrue(adapter.prepare(helper.getLevel(), altar.getBlockPos(), Direction.UP, INFUSION, pattern, inputs) == null,
                "Existing third-party items must block the operation");
        helper.assertValueEqual(tile.getInventory().getStackInSlot(0).getItem(), Items.DIRT, "Preparation must retain the busy item");
        helper.getLevel().setBlockAndUpdate(pedestal, Blocks.AIR.defaultBlockState());
        helper.assertTrue(adapter.prepare(helper.getLevel(), altar.getBlockPos(), Direction.UP, INFUSION, pattern, inputs) == null,
                "A missing physical pedestal must reject");
        helper.assertTrue(altar.getInventory().getStackInSlot(0).isEmpty(), "Every failed preparation leaves the altar untouched");
        helper.succeed();
    }

    @TestHolder("packaged_ma_awakening_rejects_essence_shortage_excess_and_stale_preparation")
    @EmptyTemplate("9")
    @GameTest(template = "empty_9x9")
    public static void awakeningRechecksLiveStructureAndExactEssenceAmounts(GameTestHelper helper) {
        var altar = awakeningStructure(helper);
        var adapter = new MysticalAltarAdapter(AltarKind.AWAKENING);
        var pattern = pattern(new ItemStack(Items.NETHERITE_INGOT));
        var shortage = awakeningInputs();
        shortage[0].remove(essence("water_essence"), 1);
        helper.assertTrue(adapter.prepare(helper.getLevel(), altar.getBlockPos(), Direction.UP, AWAKENING, pattern, shortage) == null,
                "39 essence must not satisfy a recipe requiring 40");
        var excess = awakeningInputs();
        excess[0].add(essence("water_essence"), 1);
        helper.assertTrue(adapter.prepare(helper.getLevel(), altar.getBlockPos(), Direction.UP, AWAKENING, pattern, excess) == null,
                "41 essence must not overfill a 40-item vessel or strand inputs");
        var inputs = awakeningInputs();
        CompoundTag prepared = adapter.prepare(helper.getLevel(), altar.getBlockPos(), Direction.UP, AWAKENING, pattern, inputs);
        helper.assertTrue(prepared != null, "The exact vessel quantities must prepare");
        var operation = new TestOperation(helper.getLevel(), altar.getBlockPos(), AWAKENING, prepared, inputs);
        helper.getLevel().setBlockAndUpdate(altar.getPedestalPositions().getFirst(), Blocks.AIR.defaultBlockState());
        helper.assertTrue(!adapter.advance(operation), "A removed vessel must postpone dispatch");
        helper.assertValueEqual(operation.available(AEItemKey.of(Items.NETHER_STAR)), BigInteger.ONE,
                "Stale preparation must retain all provider-owned inputs");
        helper.assertTrue(altar.getInventory().getStackInSlot(0).isEmpty(), "A stale structure must receive no center item");
        helper.succeed();
    }

    private static InfusionAltarTileEntity infusionStructure(GameTestHelper helper) {
        BlockPos center = helper.absolutePos(new BlockPos(4, 2, 4));
        helper.getLevel().setBlockAndUpdate(center, ModBlocks.INFUSION_ALTAR.get().defaultBlockState());
        var altar = (InfusionAltarTileEntity) helper.getLevel().getBlockEntity(center);
        for (BlockPos position : altar.getPedestalPositions()) {
            helper.getLevel().setBlockAndUpdate(position, ModBlocks.INFUSION_PEDESTAL.get().defaultBlockState());
        }
        return altar;
    }

    private static AwakeningAltarTileEntity awakeningStructure(GameTestHelper helper) {
        BlockPos center = helper.absolutePos(new BlockPos(4, 2, 4));
        helper.getLevel().setBlockAndUpdate(center, ModBlocks.AWAKENING_ALTAR.get().defaultBlockState());
        var altar = (AwakeningAltarTileEntity) helper.getLevel().getBlockEntity(center);
        // Deliberately group vessels before pedestals instead of following the book's alternating arrangement.
        for (int index = 0; index < 8; index++) {
            BlockState state = index < 4 ? ModBlocks.ESSENCE_VESSEL.get().defaultBlockState() :
                    ModBlocks.AWAKENING_PEDESTAL.get().defaultBlockState();
            helper.getLevel().setBlockAndUpdate(altar.getPedestalPositions().get(index), state);
        }
        return altar;
    }

    private static KeyCounter[] infusionInputs(int cycles) {
        KeyCounter inputs = new KeyCounter();
        var center = new ItemStack(Items.QUARTZ);
        center.set(DataComponents.CUSTOM_NAME, Component.literal("Physical component transfer"));
        inputs.add(AEItemKey.of(center), cycles);
        for (var item : List.of(Items.DIAMOND, Items.EMERALD, Items.MILK_BUCKET, Items.STICK,
                Items.IRON_INGOT, Items.GOLD_INGOT, Items.COPPER_INGOT, Items.REDSTONE))
            inputs.add(AEItemKey.of(item), cycles);
        return new KeyCounter[] { inputs };
    }

    private static KeyCounter[] awakeningInputs() {
        KeyCounter inputs = new KeyCounter();
        for (var item : List.of(Items.NETHER_STAR, Items.DIAMOND, Items.IRON_INGOT, Items.GOLD_INGOT, Items.EMERALD)) {
            inputs.add(AEItemKey.of(item), 1);
        }
        inputs.add(essence("air_essence"), 1);
        inputs.add(essence("earth_essence"), 39);
        inputs.add(essence("water_essence"), 40);
        inputs.add(essence("fire_essence"), 2);
        return new KeyCounter[] { inputs };
    }

    private static AEItemKey essence(String name) {
        return AEItemKey.of(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("mysticalagriculture", name)));
    }

    private static ItemStack namedShard(int amount) {
        var stack = new ItemStack(Items.AMETHYST_SHARD, amount);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Physical component transfer"));
        return stack;
    }

    private static IPatternDetails pattern(ItemStack... outputs) {
        var stacks = List.of(outputs).stream().map(stack -> new GenericStack(AEItemKey.of(stack), stack.getCount())).toList();
        return new OutputPattern(stacks);
    }

    private static void assertEmpty(GameTestHelper helper, BaseInventoryTileEntity altar, List<BlockPos> positions) {
        helper.assertTrue(altar.getInventory().getStackInSlot(0).isEmpty() && altar.getInventory().getStackInSlot(1).isEmpty(),
                "Completed altar slots must be drained");
        for (BlockPos position : positions) {
            var tile = (BaseInventoryTileEntity) helper.getLevel().getBlockEntity(position);
            helper.assertTrue(tile.getInventory().getStackInSlot(0).isEmpty(), "Consumed inputs and returned containers must leave no residue");
        }
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

    private static final class TestOperation implements PackagedMachineOperation {

        private final UUID id = UUID.randomUUID();
        private final ServerLevel level;
        private final BlockPos position;
        private final ResourceLocation recipeId;
        private CompoundTag progress;
        private final Object2ObjectOpenHashMap<AEKey, BigInteger> inputs = new Object2ObjectOpenHashMap<>();
        private final KeyCounter outputs = new KeyCounter();
        private boolean finished;
        private int revisions;

        private TestOperation(ServerLevel level, BlockPos position, ResourceLocation recipeId,
                              @Nullable CompoundTag progress, KeyCounter[] supplied) {
            if (progress == null) throw new IllegalArgumentException("Expected a successful altar preparation");
            this.level = level;
            this.position = position;
            this.recipeId = recipeId;
            this.progress = progress.copy();
            for (var counter : supplied) {
                for (var entry : counter) this.inputs.merge(entry.getKey(), BigInteger.valueOf(entry.getLongValue()), BigInteger::add);
            }
        }

        @Override
        public UUID id() {
            return this.id;
        }

        @Override
        public ServerLevel level() {
            return this.level;
        }

        @Override
        public BlockPos position() {
            return this.position;
        }

        @Override
        public Direction face() {
            return Direction.UP;
        }

        @Override
        public ResourceLocation recipeId() {
            return this.recipeId;
        }

        @Override
        public CompoundTag progress() {
            return this.progress;
        }

        @Override
        public BigInteger available(AEKey key) {
            return this.inputs.getOrDefault(key, BigInteger.ZERO);
        }

        @Override
        public void changed() {
            this.revisions++;
        }

        @Override
        public void delivered(AEKey key, long amount) {
            BigInteger next = available(key).subtract(BigInteger.valueOf(amount));
            if (amount <= 0 || next.signum() < 0) throw new IllegalStateException("Input overdraft");
            if (next.signum() == 0) this.inputs.remove(key);
            else this.inputs.put(key, next);
        }

        @Override
        public void returned(AEKey key, long amount) {
            if (amount <= 0) throw new IllegalStateException("Invalid output amount");
            this.outputs.add(key, amount);
        }

        @Override
        public void complete() {
            if (!this.inputs.isEmpty()) throw new IllegalStateException("Unsettled altar inputs");
            this.finished = true;
        }
    }
}
