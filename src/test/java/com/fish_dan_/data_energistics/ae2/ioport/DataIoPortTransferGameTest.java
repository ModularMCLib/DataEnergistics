package com.fish_dan_.data_energistics.ae2.ioport;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.ae2.key.DataFlowKey;
import com.fish_dan_.data_energistics.registry.DEItems;

import appeng.api.config.Actionable;
import appeng.api.config.FullnessMode;
import appeng.api.config.OperationMode;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.StorageCells;
import appeng.api.storage.cells.CellState;
import appeng.api.storage.cells.ICellHandler;
import appeng.api.storage.cells.ISaveProvider;
import appeng.api.storage.cells.StorageCell;
import appeng.core.definitions.AEItems;
import appeng.items.contents.CellConfig;
import appeng.me.storage.NetworkStorage;
import appeng.util.inv.AppEngInternalInventory;

import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.jspecify.annotations.Nullable;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class DataIoPortTransferGameTest {

    private static final IActionSource SOURCE = IActionSource.empty();
    private static final AEItemKey IRON = AEItemKey.of(Items.IRON_INGOT);
    private static final AEItemKey GOLD = AEItemKey.of(Items.GOLD_INGOT);
    private static final Long2ObjectOpenHashMap<TestCell> CELLS = new Long2ObjectOpenHashMap<>();
    private static boolean handlerRegistered;
    private static long nextCellId;

    private DataIoPortTransferGameTest() {}

    @TestHolder("data_io_port_network_fill_uses_actual_extraction_not_list_sentinels")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void networkFillUsesActualExtractionNotListSentinels(GameTestHelper helper) {
        ItemStack creative = AEItems.CREATIVE_CELL.stack();
        CellConfig.create(creative).setStack(0, new GenericStack(IRON, 1));
        var network = new NetworkStorage();
        network.mount(0, StorageCells.getCellInventory(creative, null));
        equal(helper, Integer.MAX_VALUE, network.getAvailableStacks().get(IRON), "AE2 creative-cell list sentinel");
        for (int speed = 0; speed <= 4; speed++) {
            for (int energy = 0; energy <= 4; energy++) {
                var fixture = fixture();
                var target = new TestCell();
                target.voiding = true;
                target.acceptedKey = IRON;
                fixture.input.setItemDirect(0, stackFor(target));
                fixture.transfer.transfer(network, OperationMode.FILL, FullnessMode.FULL, speed, energy);
                equal(helper, 1 << energy, target.insertions.size(), "Network fill must execute every parallel round");
                for (long amount : target.insertions) {
                    equal(helper, DataIoPortTransfer.quota(speed), amount, "Network fill must use the full long quota");
                }
            }
        }
        var finiteTarget = fixture();
        ItemStack cell = AEItems.ITEM_CELL_256K.stack();
        finiteTarget.input.setItemDirect(0, cell);
        long capacity = StorageCells.getCellInventory(cell, null).insert(IRON, Long.MAX_VALUE, Actionable.SIMULATE, SOURCE);
        finiteTarget.transfer.transfer(network, OperationMode.FILL, FullnessMode.FULL, 1, 0);
        equal(helper, capacity, StorageCells.getCellInventory(cell, null).getAvailableStacks().get(IRON), "One accelerated round fills an ordinary disk");
        helper.succeed();
    }

    @TestHolder("data_io_port_all_upgrade_combinations_preserve_exact_long_quotas")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void allUpgradeCombinationsPreserveExactLongQuotas(GameTestHelper helper) {
        long[] expected = { 256L, 2_305_843_009_213_693_951L, 4_611_686_018_427_387_903L,
                6_917_529_027_641_081_855L, Long.MAX_VALUE };
        for (int speed = 0; speed <= 4; speed++) {
            for (int energy = 0; energy <= 4; energy++) {
                var fixture = fixture();
                fixture.input.setItemDirect(0, new ItemStack(DEItems.DATA_CELL_INFINITY.get()));
                fixture.network.acceptedKey = DataFlowKey.INSTANCE;
                fixture.network.voiding = true;
                helper.assertTrue(fixture.transfer.transfer(fixture.network, OperationMode.EMPTY, FullnessMode.EMPTY,
                        speed, energy), "Infinite source must make progress");
                equal(helper, 1 << energy, fixture.network.insertions.size(), "Full transfer rounds");
                for (long amount : fixture.network.insertions) equal(helper, expected[speed], amount, "Per-round quota");
            }
        }
        helper.succeed();
    }

    @TestHolder("data_io_port_moves_items_fluids_and_data_in_raw_units_both_ways")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void movesItemsFluidsAndDataInRawUnitsBothWays(GameTestHelper helper) {
        checkRoundTrip(helper, AEItems.ITEM_CELL_1K.stack(), IRON);
        checkRoundTrip(helper, AEItems.FLUID_CELL_1K.stack(), AEFluidKey.of(Fluids.WATER));
        checkRoundTrip(helper, new ItemStack(DEItems.DIGITAL_STORAGE_CELL_1K.get()), DataFlowKey.INSTANCE);
        helper.succeed();
    }

    private static void checkRoundTrip(GameTestHelper helper, ItemStack stack, AEKey key) {
        var fixture = fixture();
        StorageCell cell = StorageCells.getCellInventory(stack, null);
        equal(helper, 1_024, cell.insert(key, 1_024, Actionable.MODULATE, SOURCE), "Fixture cell capacity");
        cell.persist();
        fixture.input.setItemDirect(0, stack);
        fixture.transfer.transfer(fixture.network, OperationMode.EMPTY, FullnessMode.EMPTY, 0, 0);
        equal(helper, 256, fixture.network.contents.getLong(key), "Raw-unit extraction");
        equal(helper, 768, StorageCells.getCellInventory(stack, null).getAvailableStacks().get(key), "Cell debit persisted");
        fixture.transfer.transfer(fixture.network, OperationMode.FILL, FullnessMode.FULL, 0, 0);
        equal(helper, 0, fixture.network.contents.getLong(key), "Network debit");
        equal(helper, 1_024, StorageCells.getCellInventory(stack, null).getAvailableStacks().get(key), "Cell credit persisted");
    }

    @TestHolder("data_io_port_rotates_cells_and_resources_across_saved_ticks")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void rotatesCellsAndResourcesAcrossSavedTicks(GameTestHelper helper) {
        var fixture = fixture();
        var first = new TestCell();
        first.contents.put(IRON, Long.MAX_VALUE);
        first.contents.put(GOLD, Long.MAX_VALUE);
        var second = new TestCell();
        second.contents.put(AEItemKey.of(Items.DIAMOND), 512L);
        fixture.input.setItemDirect(0, stackFor(first));
        fixture.input.setItemDirect(1, stackFor(second));
        fixture.transfer.transfer(fixture.network, OperationMode.EMPTY, FullnessMode.EMPTY, 0, 0);
        AEKey firstKey = fixture.network.contents.getLong(IRON) > 0 ? IRON : GOLD;
        AEKey nextKey = firstKey.equals(IRON) ? GOLD : IRON;
        equal(helper, 256, fixture.network.contents.getLong(firstKey), "First resource");
        var restored = new DataIoPortTransfer(fixture.input, fixture.output, SOURCE);
        restored.load(fixture.transfer.save(helper.getLevel().registryAccess()), helper.getLevel().registryAccess());
        restored.transfer(fixture.network, OperationMode.EMPTY, FullnessMode.EMPTY, 0, 0);
        equal(helper, 256, fixture.network.contents.getLong(AEItemKey.of(Items.DIAMOND)), "Next cell after reload");
        restored.transfer(fixture.network, OperationMode.EMPTY, FullnessMode.EMPTY, 0, 0);
        equal(helper, 256, fixture.network.contents.getLong(nextKey), "Next resource after reload");
        helper.succeed();
    }

    @TestHolder("data_io_port_refunds_partial_insertion_and_persists_refused_remainder")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void refundsPartialInsertionAndPersistsRefusedRemainder(GameTestHelper helper) {
        var fixture = fixture();
        var cell = new TestCell();
        cell.contents.put(IRON, 256L);
        fixture.input.setItemDirect(0, stackFor(cell));
        fixture.network.actualInsertLimit = 100L;
        fixture.transfer.transfer(fixture.network, OperationMode.EMPTY, FullnessMode.EMPTY, 0, 0);
        equal(helper, 100, fixture.network.contents.getLong(IRON), "Actual target receipt");
        equal(helper, 156, cell.contents.getLong(IRON), "Refund to source");
        helper.assertTrue(!fixture.transfer.hasPending(), "Accepted refund must not leave pending resources");

        cell.rejectInsert = true;
        fixture.network.actualInsertLimit = 0;
        fixture.transfer.transfer(fixture.network, OperationMode.EMPTY, FullnessMode.EMPTY, 0, 4);
        helper.assertTrue(fixture.transfer.hasPending(), "Refused refund remains owned by the port");
        var tag = fixture.transfer.save(helper.getLevel().registryAccess());
        var pending = GenericStack.readTag(helper.getLevel().registryAccess(), tag.getCompound("pending"));
        equal(helper, 156, pending.amount(), "Persisted remainder");
        equal(helper, 0, cell.contents.getLong(IRON), "No duplicated source contents");

        var restored = new DataIoPortTransfer(fixture.input, fixture.output, SOURCE);
        restored.load(tag, helper.getLevel().registryAccess());
        fixture.network.actualInsertLimit = Long.MAX_VALUE;
        restored.transfer(fixture.network, OperationMode.EMPTY, FullnessMode.EMPTY, 0, 4);
        equal(helper, 256, fixture.network.contents.getLong(IRON), "Conservation after recovery");
        helper.assertTrue(!restored.hasPending(), "Recovery clears pending resources exactly once");
        helper.assertTrue(cell.persistCount > 0, "Cell must be persisted after transfers");
        helper.succeed();
    }

    @TestHolder("data_io_port_respects_partition_fullness_and_blocked_output")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void respectsPartitionFullnessAndBlockedOutput(GameTestHelper helper) {
        var fixture = fixture();
        var cell = new TestCell();
        cell.acceptedKey = IRON;
        cell.capacity = 100;
        ItemStack stack = stackFor(cell);
        fixture.input.setItemDirect(0, stack);
        fixture.network.contents.put(GOLD, 100L);
        fixture.transfer.transfer(fixture.network, OperationMode.FILL, FullnessMode.FULL, 4, 4);
        equal(helper, 100, fixture.network.contents.getLong(GOLD), "Partition rejects unrelated keys");
        helper.assertTrue(!fixture.input.getStackInSlot(0).isEmpty(), "Incomplete cell stays in input");
        for (int slot = 0; slot < fixture.output.size(); slot++) fixture.output.setItemDirect(slot, AEItems.ITEM_CELL_1K.stack());
        fixture.network.contents.put(IRON, 100L);
        fixture.transfer.transfer(fixture.network, OperationMode.FILL, FullnessMode.FULL, 4, 4);
        equal(helper, 100, cell.contents.getLong(IRON), "Cell fills to capacity");
        helper.assertTrue(!fixture.input.getStackInSlot(0).isEmpty(), "Blocked output retains completed cell");
        fixture.output.setItemDirect(2, ItemStack.EMPTY);
        fixture.transfer.transfer(fixture.network, OperationMode.FILL, FullnessMode.FULL, 4, 4);
        helper.assertTrue(fixture.input.getStackInSlot(0).isEmpty(), "Completed cell leaves after output frees");
        helper.assertTrue(ItemStack.isSameItemSameComponents(stack, fixture.output.getStackInSlot(2)), "Exact cell is preserved");

        var stalled = fixture();
        stalled.input.setItemDirect(0, stackFor(new TestCell()));
        stalled.transfer.transfer(stalled.network, OperationMode.FILL, FullnessMode.HALF, 0, 4);
        helper.assertTrue(stalled.input.getStackInSlot(0).isEmpty(), "HALF ejects when no transfer is possible");
        helper.succeed();
    }

    private static Fixture fixture() {
        var input = new AppEngInternalInventory(null, 6, 1);
        var output = new AppEngInternalInventory(null, 6, 1);
        return new Fixture(input, output, new IoPortTestStorage(), new DataIoPortTransfer(input, output, SOURCE));
    }

    private static ItemStack stackFor(TestCell cell) {
        if (!handlerRegistered) {
            StorageCells.addCellHandler(new ICellHandler() {

                @Override
                public boolean isCell(ItemStack stack) {
                    return CELLS.containsKey(id(stack));
                }

                @Override
                public @Nullable StorageCell getCellInventory(ItemStack stack, @Nullable ISaveProvider provider) {
                    return CELLS.get(id(stack));
                }
            });
            handlerRegistered = true;
        }
        long id = ++nextCellId;
        CELLS.put(id, cell);
        var stack = new ItemStack(Items.PAPER);
        var tag = new CompoundTag();
        tag.putLong("data_io_test_cell", id);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    private static long id(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getLong("data_io_test_cell");
    }

    private static void equal(GameTestHelper helper, long expected, long actual, String message) {
        helper.assertTrue(expected == actual, message + ": expected " + expected + ", got " + actual);
    }

    private record Fixture(AppEngInternalInventory input, AppEngInternalInventory output,
                           IoPortTestStorage network, DataIoPortTransfer transfer) {}

    private static final class TestCell extends IoPortTestStorage implements StorageCell {

        private int persistCount;

        @Override
        public CellState getStatus() {
            if (contents.isEmpty()) return CellState.EMPTY;
            for (long value : contents.values()) if (value < capacity) return CellState.NOT_EMPTY;
            return CellState.FULL;
        }

        @Override
        public double getIdleDrain() {
            return 0;
        }

        @Override
        public boolean canFitInsideCell() {
            return false;
        }

        @Override
        public void persist() {
            persistCount++;
        }
    }
}
