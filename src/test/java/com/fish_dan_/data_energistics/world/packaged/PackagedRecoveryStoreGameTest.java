package com.fish_dan_.data_energistics.world.packaged;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import java.util.UUID;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class PackagedRecoveryStoreGameTest {

    private PackagedRecoveryStoreGameTest() {}

    @TestHolder("packaged_receipt_redemption_survives_dimension_data_reload")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void redeemedReceiptStaysConsumedAfterReload(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        var store = new PackagedRecoveryStore();
        var id = UUID.randomUUID();
        var origin = new BlockPos(12, 64, -4);
        var state = new CompoundTag();
        state.putString("amount", "18446744073709551614");
        store.deposit(id, origin, "standalone", state, new ListTag());
        state.putString("amount", "1");
        var restored = PackagedRecoveryStore.load(store.save(new CompoundTag(), registries), registries);
        helper.assertTrue(restored.inspect(id, origin.above(), "standalone") == null, "A wrong origin cannot access a persisted escrow");
        helper.assertTrue(restored.inspect(id, origin, "adaptive") == null, "A wrong provider kind cannot access a persisted escrow");
        var payload = restored.inspect(id, origin, "standalone");
        helper.assertTrue(payload != null, "Unredeemed receipts must survive the world-data codec");
        helper.assertValueEqual(payload.getCompound("state").getString("amount"), "18446744073709551614", "Deposited assets must not alias their source tag");
        payload.getCompound("state").putString("amount", "2");
        helper.assertValueEqual(restored.inspect(id, origin, "standalone").getCompound("state").getString("amount"), "18446744073709551614", "Read-only inspection must not mutate the stored escrow");
        restored.redeem(id);
        var afterRestart = PackagedRecoveryStore.load(restored.save(new CompoundTag(), registries), registries);
        helper.assertTrue(afterRestart.inspect(id, origin, "standalone") == null, "A copied receipt must stay invalid after restart");
        helper.succeed();
    }
}
