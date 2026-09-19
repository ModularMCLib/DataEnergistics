package com.fish_dan_.data_energistics.integration.crafting.packaged.botania;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.crafting.packaged.execution.PackagedOperationState;
import com.fish_dan_.data_energistics.mixin.botania.AlfheimPortalAccessor;
import com.fish_dan_.data_energistics.world.packaged.PackagedMachineClaims;

import appeng.api.crafting.IPatternDetails;
import appeng.api.ids.AEComponents;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.pattern.EncodedProcessingPattern;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import vazkii.botania.api.block.PetalApothecary;
import vazkii.botania.common.block.BotaniaBlocks;
import vazkii.botania.common.block.block_entity.AlfheimPortalBlockEntity;
import vazkii.botania.common.block.block_entity.PetalApothecaryBlockEntity;
import vazkii.botania.common.block.block_entity.RunicAltarBlockEntity;
import vazkii.botania.common.block.block_entity.TerrestrialAgglomerationPlateBlockEntity;
import vazkii.botania.common.block.block_entity.mana.ManaPoolBlockEntity;

import java.math.BigInteger;
import java.util.List;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class BotaniaMachineGameTest {

    private BotaniaMachineGameTest() {}

    @TestHolder("packaged_botania_petal_actual_water_seed_consumption_and_idempotent_encoding")
    @EmptyTemplate("9")
    @GameTest(template = "empty_9x9")
    public static void apothecaryConsumesRealWaterAndSeed(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos position = helper.absolutePos(new BlockPos(4, 2, 4));
        level.setBlockAndUpdate(position, BotaniaBlocks.PETAL_APOTHECARY.defaultBlockState());
        var tile = (PetalApothecaryBlockEntity) level.getBlockEntity(position);
        var adapter = new BotaniaMachineAdapter(BotaniaMachineKind.PETAL);
        ResourceLocation recipe = id("petal_apothecary/pure_daisy");
        ItemStack encoded = new ItemStack(Items.PAPER);
        encoded.set(DataComponents.CUSTOM_NAME, Component.literal("Keep encoding metadata"));
        encoded.set(AEComponents.ENCODED_PROCESSING_PATTERN, new EncodedProcessingPattern(
                List.of(new GenericStack(item("white_mystical_petal"), 4)), List.of(new GenericStack(item("pure_daisy"), 1))));
        ItemStack completed = adapter.completeEncoding(level, recipe, encoded);
        helper.assertTrue(completed != null, "Exact petal recipe must gain water and its actual reagent");
        ItemStack repeated = adapter.completeEncoding(level, recipe, completed);
        helper.assertTrue(repeated != null && ItemStack.matches(completed, repeated), "Reencoding must not duplicate water, seed or bucket");
        helper.assertValueEqual(completed.get(DataComponents.CUSTOM_NAME), encoded.get(DataComponents.CUSTOM_NAME), "Unrelated encoded metadata must remain");
        var processing = completed.get(AEComponents.ENCODED_PROCESSING_PATTERN);
        var input = counters(processing.sparseInputs());
        var pattern = new OutputPattern(processing.sparseOutputs());
        var operation = begin(helper, adapter, position, recipe, pattern, input);
        operation.advance(level, adapter);
        helper.assertTrue(operation.failure() == null, "Native petal collision and bucket draining must succeed");
        operation.advance(level, adapter);
        assertComplete(helper, operation);
        helper.assertTrue(tile.isEmpty() && tile.getFluid() == PetalApothecary.State.EMPTY, "Crafting must consume the actual water and petals");
        assertReturned(helper, operation, item("pure_daisy"), 1);
        assertReturned(helper, operation, AEItemKey.of(Items.BUCKET), 1);
        release(helper, operation);
        helper.succeed();
    }

    @TestHolder("packaged_botania_mana_pool_real_mana_and_unrelated_drop_isolation")
    @EmptyTemplate("9")
    @GameTest(template = "empty_9x9")
    public static void manaPoolChargesOnlyForItsOwnedInput(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos position = helper.absolutePos(new BlockPos(4, 2, 4));
        level.setBlockAndUpdate(position, BotaniaBlocks.MANA_POOL.defaultBlockState());
        var pool = (ManaPoolBlockEntity) level.getBlockEntity(position);
        var adapter = new BotaniaMachineAdapter(BotaniaMachineKind.MANA);
        var recipe = id("mana_infusion/manasteel_ingot");
        var supplied = counters(List.of(new GenericStack(AEItemKey.of(Items.IRON_INGOT), 1)));
        var pattern = new OutputPattern(List.of(new GenericStack(item("manasteel_ingot"), 1)));
        helper.assertTrue(adapter.prepare(level, position, Direction.UP, recipe, pattern, supplied) == null, "Insufficient pool mana must reject before accepting inputs");
        pool.receiveMana(3000);
        var operation = begin(helper, adapter, position, recipe, pattern, supplied);
        var foreign = new ItemEntity(level, position.getX() + 0.5, position.getY() + 0.3, position.getZ() + 0.5, new ItemStack(Items.IRON_INGOT));
        level.addFreshEntity(foreign);
        helper.assertTrue(!pool.collideEntityItem(foreign), "A claimed pool must ignore another operation's physical drop");
        operation.advance(level, adapter);
        operation.advance(level, adapter);
        assertComplete(helper, operation);
        helper.assertValueEqual(pool.getCurrentMana(), 0, "Native infusion must consume exactly 3000 mana");
        helper.assertTrue(foreign.isAlive() && foreign.getItem().is(Items.IRON_INGOT), "Unrelated input must not be consumed");
        assertReturned(helper, operation, item("manasteel_ingot"), 1);
        release(helper, operation);
        // After release the same machine must retain ordinary Botania behavior.
        pool.receiveMana(3000);
        helper.assertTrue(pool.collideEntityItem(foreign), "An unclaimed pool must accept ordinary item drops");
        helper.succeed();
    }

    @TestHolder("packaged_botania_runic_altar_waits_for_native_mana_and_consumes_owned_reagent")
    @EmptyTemplate("9")
    @GameTest(template = "empty_9x9", timeoutTicks = 120)
    public static void runeAltarUsesItsRealManaAndClosingReagent(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos position = helper.absolutePos(new BlockPos(4, 2, 4));
        level.setBlockAndUpdate(position, BotaniaBlocks.RUNIC_ALTAR.defaultBlockState());
        var altar = (RunicAltarBlockEntity) level.getBlockEntity(position);
        ResourceLocation recipe = id("runic_altar/rune_of_air");
        var selected = List.of(item("mana_powder").toStack(), item("manasteel_ingot").toStack(),
                new ItemStack(Items.WHITE_CARPET), new ItemStack(Items.FEATHER), new ItemStack(Items.STRING));
        var expanded = BotaniaPatternEncoding.augment(level, level.getRecipeManager().byKey(recipe).orElseThrow(), selected);
        helper.assertTrue(expanded != null, "Rune encoding must contain the actual livingrock reagent");
        var adapter = new BotaniaMachineAdapter(BotaniaMachineKind.RUNE);
        var operation = begin(helper, adapter, position, recipe, pattern(expanded.outputs()), counters(stacks(expanded.inputs())));
        operation.advance(level, adapter);
        var foreign = new ItemEntity(level, position.getX() + 0.5, position.getY() + 0.5, position.getZ() + 0.5,
                new ItemStack(BotaniaBlocks.LIVINGROCK));
        foreign.setNoGravity(true);
        level.addFreshEntity(foreign);
        boolean[] suppliedMana = { false };
        helper.onEachTick(() -> {
            if (!suppliedMana[0] && altar.getTargetMana() > 0) {
                altar.receiveMana(altar.getTargetMana());
                suppliedMana[0] = true;
            }
            operation.advance(level, adapter);
        });
        helper.succeedWhen(() -> {
            assertComplete(helper, operation);
            helper.assertTrue(suppliedMana[0] && altar.getCurrentMana() == 0, "Real rune mana must be consumed");
            helper.assertTrue(foreign.isAlive() && foreign.getItem().is(BotaniaBlocks.LIVINGROCK.asItem()), "The unrelated closing reagent must survive");
            assertReturned(helper, operation, item("rune_of_air"), 2);
            release(helper, operation);
        });
    }

    @TestHolder("packaged_botania_terra_plate_native_platform_mana_and_foreign_drop_isolation")
    @EmptyTemplate("9")
    @GameTest(template = "empty_9x9", timeoutTicks = 120)
    public static void terraPlateRunsItsNativeMultiblockRecipe(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos position = helper.absolutePos(new BlockPos(4, 3, 4));
        TerrestrialAgglomerationPlateBlockEntity.MULTIBLOCK.get().place(level, position.below(), Rotation.NONE);
        var plate = (TerrestrialAgglomerationPlateBlockEntity) level.getBlockEntity(position);
        helper.assertTrue(plate != null, "Native Patchouli platform placement must include the plate");
        var adapter = new BotaniaMachineAdapter(BotaniaMachineKind.TERRA);
        var recipe = id("terrestrial_agglomeration_plate/terrasteel_ingot");
        var supplied = counters(List.of(new GenericStack(item("manasteel_ingot"), 1), new GenericStack(item("mana_pearl"), 1),
                new GenericStack(item("mana_diamond"), 1)));
        var pattern = new OutputPattern(List.of(new GenericStack(item("terrasteel_ingot"), 1)));
        var operation = begin(helper, adapter, position, recipe, pattern, supplied);
        operation.advance(level, adapter);
        helper.assertTrue(operation.failure() == null, "The plate must accept all physical inputs");
        var foreign = new ItemEntity(level, position.getX() + 0.5, position.getY() + 0.2, position.getZ() + 0.5, new ItemStack(Items.DIRT));
        foreign.setNoGravity(true);
        level.addFreshEntity(foreign);
        plate.receiveMana(500000);
        helper.onEachTick(() -> operation.advance(level, adapter));
        helper.succeedWhen(() -> {
            assertComplete(helper, operation);
            helper.assertTrue(foreign.isAlive() && foreign.getItem().is(Items.DIRT), "A claimed plate may not consume a foreign drop");
            helper.assertValueEqual(plate.getCurrentMana(), 0, "The native plate must spend its crafting mana");
            assertReturned(helper, operation, item("terrasteel_ingot"), 1);
            release(helper, operation);
        });
    }

    @TestHolder("packaged_botania_alfheim_native_open_portal_and_pylon_mana")
    @EmptyTemplate("9")
    @GameTest(template = "empty_9x9", timeoutTicks = 160)
    public static void alfheimPortalTradesThroughItsOwnTick(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos position = helper.absolutePos(new BlockPos(4, 2, 4));
        AlfheimPortalBlockEntity.MULTIBLOCK.get().place(level, position, Rotation.NONE);
        var portal = (AlfheimPortalBlockEntity) level.getBlockEntity(position);
        helper.assertTrue(portal != null, "Native portal structure placement must include its core");
        var pools = new ObjectArrayList<ManaPoolBlockEntity>();
        for (int offset : new int[] { -3, 3 }) {
            BlockPos poolPos = position.offset(offset, 0, 2);
            level.setBlockAndUpdate(poolPos, BotaniaBlocks.MANA_POOL.defaultBlockState());
            var pool = (ManaPoolBlockEntity) level.getBlockEntity(poolPos);
            pool.receiveMana(100250);
            pools.add(pool);
            level.setBlockAndUpdate(poolPos.above(), BotaniaBlocks.NATURA_PYLON.defaultBlockState());
        }
        helper.assertTrue(portal.onUsedByWand(null, ItemStack.EMPTY, Direction.UP), "The native portal structure must open with the wand");
        var adapter = new BotaniaMachineAdapter(BotaniaMachineKind.PORTAL);
        var operations = new ObjectArrayList<PackagedOperationState>(1);
        helper.runAtTickTime(70, () -> {
            helper.assertTrue(portal.ticksOpen > AlfheimPortalBlockEntity.TICKS_UNTIL_FULLY_OPENED,
                    "The portal must finish its native opening ticks: " + portal.ticksOpen);
            helper.assertValueEqual(portal.locatePylons(true).size(), 2, "The native portal must find exactly the two test pylons");
            for (var pool : pools) helper.assertValueEqual(pool.getCurrentMana(), 250, "The native opening must consume 100000 mana from each pool");
            helper.assertTrue(((AlfheimPortalAccessor) portal).dataEnergistics$pendingTradeItems().isEmpty(),
                    "A newly opened portal must have no pending trade items: " +
                            ((AlfheimPortalAccessor) portal).dataEnergistics$pendingTradeItems());
            helper.assertTrue(AlfheimPortalBlockEntity.MULTIBLOCK.get().validate(level, position) != null,
                    "The opened portal frame must still match the native multiblock");
            for (int chunkX = (position.getX() - 5) >> 4; chunkX <= (position.getX() + 5) >> 4; chunkX++) {
                for (int chunkZ = (position.getZ() - 5) >> 4; chunkZ <= (position.getZ() + 5) >> 4; chunkZ++) {
                    helper.assertTrue(level.hasChunk(chunkX, chunkZ), "The portal's complete pylon search area must be loaded");
                }
            }
            var supplied = counters(List.of(new GenericStack(item("managlass"), 1)));
            var pattern = new OutputPattern(List.of(new GenericStack(item("alfglass"), 1)));
            helper.assertTrue(BotaniaRecipePlan.prepare(level, portal,
                    level.getRecipeManager().byKey(id("elven_trade/alfglass")).orElseThrow(), pattern, supplied, false) != null,
                    "The exact native elven trade must match its supplied items and outputs");
            var operation = begin(helper, adapter, position, id("elven_trade/alfglass"), pattern, supplied);
            operations.add(operation);
            operation.advance(level, adapter);
        });
        // Register callbacks before ticking: onEachTick mutates GameTest's scheduled-task map.
        // Adding it from a running scheduled callback can rehash that map and replay admission.
        helper.onEachTick(() -> {
            if (!operations.isEmpty()) operations.getFirst().advance(level, adapter);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(!operations.isEmpty(), "The portal must finish opening before admission");
            helper.assertValueEqual(operations.size(), 1, "The test must admit exactly one trade");
            var operation = operations.getFirst();
            assertComplete(helper, operation);
            assertReturned(helper, operation, item("alfglass"), 1);
            for (var pool : pools) helper.assertValueEqual(pool.getCurrentMana(), 0, "Opening and trade must spend the actual pylon mana");
            release(helper, operation);
        });
    }

    private static PackagedOperationState begin(GameTestHelper helper, BotaniaMachineAdapter adapter, BlockPos position,
                                                ResourceLocation recipe, IPatternDetails pattern, KeyCounter[] inputs) {
        var prepared = adapter.prepare(helper.getLevel(), position, Direction.UP, recipe, pattern, inputs);
        helper.assertTrue(prepared != null, "Native registered Botania recipe and its physical prerequisites must prepare: " + recipe);
        var operation = new PackagedOperationState(adapter.id(), recipe, position, Direction.UP, prepared, inputs);
        helper.assertTrue(PackagedMachineClaims.get(helper.getLevel()).acquire(position, operation.id()), "Test operation must own the real machine");
        return operation;
    }

    private static void assertComplete(GameTestHelper helper, PackagedOperationState operation) {
        helper.assertTrue(operation.failure() == null, "Operation failed: " + operation.failure());
        helper.assertTrue(operation.save(helper.getLevel().registryAccess()).getBoolean("complete"), "Actual machine output must settle the operation");
    }

    private static void assertReturned(GameTestHelper helper, PackagedOperationState operation, AEItemKey key, long amount) {
        var entries = operation.save(helper.getLevel().registryAccess()).getList("outputs", Tag.TAG_COMPOUND);
        BigInteger found = BigInteger.ZERO;
        for (int index = 0; index < entries.size(); index++) {
            var entry = entries.getCompound(index);
            if (key.equals(AEKey.fromTagGeneric(helper.getLevel().registryAccess(), entry.getCompound("key")))) found = found.add(new BigInteger(entry.getString("amount")));
        }
        helper.assertValueEqual(found, BigInteger.valueOf(amount), "Only actual owned output must enter the return ledger");
    }

    private static void release(GameTestHelper helper, PackagedOperationState operation) {
        var claims = PackagedMachineClaims.get(helper.getLevel());
        if (operation.id().equals(claims.owner(operation.position()))) claims.release(operation.position(), operation.id());
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("botania", path);
    }

    private static AEItemKey item(String path) {
        return AEItemKey.of(BuiltInRegistries.ITEM.get(id(path)));
    }

    private static KeyCounter[] counters(List<GenericStack> stacks) {
        var input = new KeyCounter();
        for (var stack : stacks) if (stack != null) input.add(stack.what(), stack.amount());
        return new KeyCounter[] { input };
    }

    private static List<GenericStack> stacks(List<ItemStack> stacks) {
        return stacks.stream().map(stack -> new GenericStack(AEItemKey.of(stack), stack.getCount())).toList();
    }

    private static IPatternDetails pattern(List<ItemStack> outputs) {
        return new OutputPattern(stacks(outputs));
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
