package com.fish_dan_.data_energistics.blockentity.ioport;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.block.ioport.DataIoPortBlock;
import com.fish_dan_.data_energistics.registry.DEBlocks;
import com.fish_dan_.data_energistics.registry.DEItems;

import appeng.api.config.Actionable;
import appeng.api.config.FullnessMode;
import appeng.api.config.OperationMode;
import appeng.api.config.RedstoneMode;
import appeng.api.config.Settings;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.StorageCells;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.util.SettingsFrom;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class DataIoPortGameTest {

    private static final BlockPos PORT = new BlockPos(2, 1, 2);
    private static final BlockPos ENERGY = new BlockPos(1, 1, 2);
    private static final BlockPos DRIVE = new BlockPos(3, 1, 2);
    private static final BlockPos SIGNAL = new BlockPos(2, 1, 1);
    private static final AEItemKey IRON = AEItemKey.of(Items.IRON_INGOT);

    private DataIoPortGameTest() {}

    @TestHolder("data_io_port_live_upgrades_apply_to_the_next_grid_call")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5", timeoutTicks = 160)
    public static void liveUpgradesApplyToTheNextGridCall(GameTestHelper helper) {
        var port = placeNetwork(helper);
        helper.startSequence().thenWaitUntil(() -> assertOnline(helper, port)).thenExecute(() -> {
            var source = IActionSource.ofMachine(port);
            var storage = port.getMainNode().getGrid().getStorageService().getInventory();
            for (int speed = 4; speed >= 0; speed--) {
                for (int energy = 4; energy >= 0; energy--) {
                    port.getInternalInventory().clear();
                    port.getUpgrades().clear();
                    storage.extract(IRON, Long.MAX_VALUE, Actionable.MODULATE, source);
                    for (int i = 0; i < speed; i++) port.getUpgrades().addItems(AEItems.SPEED_CARD.stack());
                    for (int i = 0; i < energy; i++) port.getUpgrades().addItems(new ItemStack(DEItems.CARD_SABER_ENERGY.get()));
                    port.getInternalInventory().setItemDirect(0, ironCell(500_000));
                    port.tickingRequest(port.getMainNode().getNode(), 100);
                    long expected = speed == 0 ? 256L << energy : 500_000L;
                    equal(helper, expected, storage.extract(IRON, Long.MAX_VALUE, Actionable.SIMULATE, source),
                            "Dynamic upgrades and delayed tick must use one scheduling call");
                }
            }
            port.getUpgrades().clear();
            port.getInternalInventory().clear();
        }).thenSucceed();
    }

    @TestHolder("data_io_port_redstone_modes_and_disconnect_stop_actual_transfer")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5", timeoutTicks = 200)
    public static void redstoneModesAndDisconnectStopActualTransfer(GameTestHelper helper) {
        var port = placeNetwork(helper);
        helper.startSequence().thenWaitUntil(() -> assertOnline(helper, port)).thenExecute(() -> {
            port.getInternalInventory().setItemDirect(0, ironCell(10_000));
            port.getUpgrades().addItems(AEItems.REDSTONE_CARD.stack());
            port.getConfigManager().putSetting(Settings.REDSTONE_CONTROLLED, RedstoneMode.HIGH_SIGNAL);
            equal(helper, 0, transferDelta(port), "High mode without a signal");
            helper.setBlock(SIGNAL, Blocks.REDSTONE_BLOCK);
            port.updateRedstoneState();
            equal(helper, 256, transferDelta(port), "High mode with a signal");
            port.getConfigManager().putSetting(Settings.REDSTONE_CONTROLLED, RedstoneMode.IGNORE);
            equal(helper, 256, transferDelta(port), "Ignore mode with installed redstone card");
            port.getConfigManager().putSetting(Settings.REDSTONE_CONTROLLED, RedstoneMode.LOW_SIGNAL);
            equal(helper, 0, transferDelta(port), "Low mode with a signal");
            helper.setBlock(SIGNAL, Blocks.AIR);
            port.updateRedstoneState();
            equal(helper, 256, transferDelta(port), "Low mode without a signal");
            port.getConfigManager().putSetting(Settings.REDSTONE_CONTROLLED, RedstoneMode.SIGNAL_PULSE);
            equal(helper, 0, transferDelta(port), "Pulse mode before an edge");
            helper.setBlock(SIGNAL, Blocks.REDSTONE_BLOCK);
            port.updateRedstoneState();
            equal(helper, 256, transferDelta(port), "One scheduling call per rising edge");
            equal(helper, 0, transferDelta(port), "A sustained signal must not repeat a pulse");
            port.getConfigManager().putSetting(Settings.REDSTONE_CONTROLLED, RedstoneMode.HIGH_SIGNAL);
            helper.setBlock(ENERGY, Blocks.AIR);
        }).thenWaitUntil(() -> helper.assertTrue(!port.getMainNode().isActive(), "Port must become inactive after power loss"))
                .thenExecute(() -> equal(helper, 0, transferDelta(port), "Disconnected port must preserve its cell"))
                .thenSucceed();
    }

    @TestHolder("data_io_port_all_orientations_filter_automation_and_limit_upgrades")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void allOrientationsFilterAutomationAndLimitUpgrades(GameTestHelper helper) {
        helper.setBlock(PORT, DEBlocks.DATA_IO_PORT.get());
        DataIoPortBlockEntity port = helper.getBlockEntity(PORT);
        for (var state : DEBlocks.DATA_IO_PORT.get().getStateDefinition().getPossibleStates()) {
            helper.setBlock(PORT, state);
            port.getInternalInventory().clear();
            var input = port.getExposedItemHandler(port.getTop());
            helper.assertTrue(!input.insertItem(0, new ItemStack(Items.STONE), false).isEmpty(), "Input rejects non-cells");
            helper.assertTrue(input.insertItem(0, AEItems.ITEM_CELL_1K.stack(), false).isEmpty(), "Relative top accepts a cell");
            helper.assertTrue(input.extractItem(0, 1, false).isEmpty(), "Automation cannot extract unfinished cells");
            var side = port.getExposedItemHandler(port.getFront());
            helper.assertTrue(!side.insertItem(0, AEItems.ITEM_CELL_1K.stack(), false).isEmpty(), "Output face rejects insertion");
            port.getInternalInventory().setItemDirect(6, AEItems.ITEM_CELL_1K.stack());
            helper.assertTrue(!side.extractItem(0, 1, false).isEmpty(), "Output face extracts completed cells");
            helper.assertTrue(port.getExposedItemHandler(null).extractItem(0, 1, false).isEmpty(), "Unsided capability retains input protection");
        }
        for (int i = 0; i < 4; i++) helper.assertTrue(port.getUpgrades().addItems(AEItems.SPEED_CARD.stack()).isEmpty(), "Four speed cards fit");
        helper.assertTrue(!port.getUpgrades().addItems(AEItems.SPEED_CARD.stack()).isEmpty(), "Fifth speed card is rejected");
        for (int i = 0; i < 4; i++) helper.assertTrue(port.getUpgrades().addItems(new ItemStack(DEItems.CARD_SABER_ENERGY.get())).isEmpty(), "Four energy cards fit");
        helper.assertTrue(!port.getUpgrades().addItems(new ItemStack(DEItems.CARD_SABER_ENERGY.get())).isEmpty(), "Fifth energy card is rejected");
        helper.assertTrue(!port.getUpgrades().addItems(AEItems.REDSTONE_CARD.stack()).isEmpty(), "Eight installed upgrades leave no room for a redstone card");
        port.getUpgrades().setItemDirect(7, ItemStack.EMPTY);
        helper.assertTrue(port.getUpgrades().addItems(AEItems.REDSTONE_CARD.stack()).isEmpty(), "Redstone card fits after freeing one upgrade slot");
        equal(helper, 8, port.getUpgrades().size(), "Upgrade slot count");
        helper.succeed();
    }

    @TestHolder("data_io_port_reload_and_wrench_preserve_cells_upgrades_and_pending_resources")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void reloadAndWrenchPreserveCellsUpgradesAndPendingResources(GameTestHelper helper) {
        helper.setBlock(PORT, DEBlocks.DATA_IO_PORT.get());
        DataIoPortBlockEntity port = helper.getBlockEntity(PORT);
        port.getInternalInventory().setItemDirect(0, ironCell(1_024));
        port.getInternalInventory().setItemDirect(6, AEItems.ITEM_CELL_1K.stack());
        port.getUpgrades().addItems(AEItems.SPEED_CARD.stack());
        port.getConfigManager().putSetting(Settings.OPERATION_MODE, OperationMode.FILL);
        port.getConfigManager().putSetting(Settings.FULLNESS_MODE, FullnessMode.FULL);
        var tag = new CompoundTag();
        port.saveAdditional(tag, helper.getLevel().registryAccess());
        var transfer = tag.getCompound("transfer");
        transfer.put("pending", GenericStack.writeTag(helper.getLevel().registryAccess(), new GenericStack(IRON, Long.MAX_VALUE)));
        port.loadTag(tag, helper.getLevel().registryAccess());
        var snapshot = new CompoundTag();
        port.saveAdditional(snapshot, helper.getLevel().registryAccess());
        equal(helper, Long.MAX_VALUE, GenericStack.readTag(helper.getLevel().registryAccess(), snapshot.getCompound("transfer").getCompound("pending")).amount(), "Long pending survives reload");
        equal(helper, 1_024, StorageCells.getCellInventory(port.getInternalInventory().getStackInSlot(0), null).getAvailableStacks().get(IRON), "Stored cell contents survive reload");

        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockPos position = port.getBlockPos();
        port.disassembleWithWrench(player, helper.getLevel(),
                new BlockHitResult(Vec3.atCenterOf(position), Direction.UP, position, false), AEItems.CERTUS_QUARTZ_WRENCH.stack());
        equal(helper, 1, player.getInventory().countItem(DEItems.DATA_IO_PORT.get()), "One port after wrench removal");
        equal(helper, 1, player.getInventory().countItem(AEItems.SPEED_CARD.asItem()), "One upgrade after wrench removal");
        equal(helper, 1, player.getInventory().countItem(AEItems.ITEM_CELL_64K.asItem()), "Input cell returned once");
        equal(helper, 1, player.getInventory().countItem(AEItems.ITEM_CELL_1K.asItem()), "Output cell returned once");
        ItemStack dropped = ItemStack.EMPTY;
        for (ItemStack stack : player.getInventory().items) if (stack.is(DEItems.DATA_IO_PORT.get())) dropped = stack;
        helper.setBlock(PORT, DEBlocks.DATA_IO_PORT.get());
        DataIoPortBlockEntity replacement = helper.getBlockEntity(PORT);
        replacement.importSettings(SettingsFrom.DISMANTLE_ITEM, dropped.getComponents(), player);
        var restored = new CompoundTag();
        replacement.saveAdditional(restored, helper.getLevel().registryAccess());
        equal(helper, Long.MAX_VALUE, GenericStack.readTag(helper.getLevel().registryAccess(), restored.getCompound("transfer").getCompound("pending")).amount(), "Pending moves with dismantled block");
        helper.assertTrue(replacement.getConfigManager().getSetting(Settings.OPERATION_MODE) == OperationMode.FILL, "Direction survives dismantling");
        helper.succeed();
    }

    private static DataIoPortBlockEntity placeNetwork(GameTestHelper helper) {
        helper.setBlock(ENERGY, AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(PORT, DEBlocks.DATA_IO_PORT.get());
        helper.setBlock(DRIVE, AEBlocks.DRIVE.block());
        DriveBlockEntity drive = helper.getBlockEntity(DRIVE);
        drive.getInternalInventory().setItemDirect(0, AEItems.ITEM_CELL_256K.stack());
        return helper.getBlockEntity(PORT);
    }

    private static void assertOnline(GameTestHelper helper, DataIoPortBlockEntity port) {
        helper.assertTrue(port.getMainNode().isActive(), "Port requires power and a channel");
        helper.assertTrue(port.getBlockState().getValue(DataIoPortBlock.POWERED), "Powered model state must track the node");
    }

    private static ItemStack ironCell(long amount) {
        ItemStack stack = AEItems.ITEM_CELL_64K.stack();
        var cell = StorageCells.getCellInventory(stack, null);
        long inserted = cell.insert(IRON, amount, Actionable.MODULATE, IActionSource.empty());
        if (inserted != amount) throw new IllegalArgumentException("Fixture exceeds cell capacity");
        cell.persist();
        return stack;
    }

    private static long transferDelta(DataIoPortBlockEntity port) {
        ItemStack stack = port.getInternalInventory().getStackInSlot(0);
        long before = StorageCells.getCellInventory(stack, null).getAvailableStacks().get(IRON);
        port.tickingRequest(port.getMainNode().getNode(), 1);
        return before - StorageCells.getCellInventory(stack, null).getAvailableStacks().get(IRON);
    }

    private static void equal(GameTestHelper helper, long expected, long actual, String message) {
        helper.assertTrue(expected == actual, message + ": expected " + expected + ", got " + actual);
    }
}
