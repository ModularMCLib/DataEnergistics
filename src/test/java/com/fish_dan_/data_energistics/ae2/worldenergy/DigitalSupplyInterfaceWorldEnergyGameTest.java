package com.fish_dan_.data_energistics.ae2.worldenergy;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.ae2.key.DataKey;
import com.fish_dan_.data_energistics.api.registry.worldenergy.WorldEnergyTransferContext;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class DigitalSupplyInterfaceWorldEnergyGameTest {

    private static final IActionSource SOURCE = IActionSource.empty();

    private DigitalSupplyInterfaceWorldEnergyGameTest() {}

    @TestHolder("digital_supply_interface_presence_markers_are_amount_one_and_read_only")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void presenceMarkersAreAmountOneAndReadOnly(GameTestHelper helper) {
        PresenceMarkerStorage storage = new PresenceMarkerStorage(() -> {});
        AEKey key = DataKey.of();
        storage.loadMarker(key, 100L);
        storage.loadMarker(key, 2L);

        KeyCounter available = new KeyCounter();
        storage.getAvailableStacks(available);
        equal(helper, 1L, available.get(key), "A positive marker amount must normalize to one");
        equal(helper, 0L, storage.insert(key, 100L, Actionable.MODULATE, SOURCE), "Marker storage must reject insertion");
        equal(helper, 0L, storage.extract(key, 100L, Actionable.MODULATE, SOURCE), "Marker storage must reject extraction");
        helper.succeed();
    }

    @TestHolder("digital_supply_interface_transfer_simulation_and_late_mismatch_preserve_accounting")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void transferSimulationAndLateMismatchPreserveAccounting(GameTestHelper helper) {
        FakeTransfer transfer = new FakeTransfer(10L);
        WorldEnergyTransferContext.TransferResult rejected = transfer.networkToWorld(
                DataKey.of(), 8L, (amount, simulate) -> simulate ? 0L : amount);
        equal(helper, 8L, rejected.requested(), "Rejected target must retain requested amount");
        equal(helper, 0L, rejected.transferred(), "Rejected target must transfer nothing");
        equal(helper, 10L, transfer.network, "Simulation failure must not change network quantity");

        WorldEnergyTransferContext.TransferResult partial = transfer.networkToWorld(
                DataKey.of(), 8L, (amount, simulate) -> simulate ? 6L : 4L);
        equal(helper, 4L, partial.transferred(), "Late target mismatch must settle actual accepted amount");
        equal(helper, 0L, partial.unrecovered(), "Network compensation must restore the unaccepted amount");
        equal(helper, 6L, transfer.network, "Only actual world acceptance may leave the network");
        helper.succeed();
    }

    private static void equal(GameTestHelper helper, long expected, long actual, String message) {
        if (expected != actual) {
            throw new GameTestAssertException(message + ": expected " + expected + ", got " + actual);
        }
    }

    private static final class FakeTransfer implements WorldEnergyTransferContext {

        private long network;

        private FakeTransfer(long network) {
            this.network = network;
        }

        @Override
        public long simulateNetworkExtract(AEKey key, long amount) {
            return Math.min(this.network, amount);
        }

        @Override
        public long commitNetworkExtract(AEKey key, long amount) {
            long extracted = Math.min(this.network, amount);
            this.network -= extracted;
            return extracted;
        }

        @Override
        public long simulateNetworkInsert(AEKey key, long amount) {
            return amount;
        }

        @Override
        public long commitNetworkInsert(AEKey key, long amount) {
            this.network += amount;
            return amount;
        }
    }
}
