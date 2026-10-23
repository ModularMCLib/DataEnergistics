package com.fish_dan_.data_energistics.common.world.trinity;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.crafting.trinity.execution.state.inventory.TrinityExactWorkingInventory;
import com.fish_dan_.data_energistics.common.trinity.core.TrinityDataCoreStorageProfile;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEItemKey;
import appeng.crafting.inv.ListCraftingInventory;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import java.math.BigInteger;
import java.util.UUID;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class TrinityDataCoreStorageSavedDataGameTest {

    private TrinityDataCoreStorageSavedDataGameTest() {}

    @TestHolder("trinity_unlimited_storage_accepts_exact_cpu_return_above_long")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void acceptsExactCpuReturnAboveLong(GameTestHelper helper) {
        TrinityDataCoreStorageSavedData storage = new TrinityDataCoreStorageSavedData();
        UUID storageId = UUID.randomUUID();
        AEItemKey key = AEItemKey.of(Items.IRON_INGOT);
        BigInteger amount = BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.valueOf(23L));

        BigInteger inserted = storage.insertExact(
                storageId,
                key,
                amount,
                Actionable.MODULATE,
                TrinityDataCoreStorageProfile.UNLIMITED);

        helper.assertValueEqual(inserted, amount, "Unlimited Trinity storage accepts the complete exact amount");
        helper.assertValueEqual(storage.amount(storageId, key), amount, "Saved storage retains the exact amount");
        TrinityDataCoreStorageSavedData restored = TrinityDataCoreStorageSavedData.load(
                storage.save(new CompoundTag(), helper.getLevel().registryAccess()),
                helper.getLevel().registryAccess());
        helper.assertValueEqual(restored.amount(storageId, key), amount, "Exact CPU returns survive storage serialization");
        helper.succeed();
    }

    @TestHolder("trinity_cpu_exact_overflow_transfers_to_unlimited_storage_in_one_exact_amount")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void transfersExactOverflowInOneAmount(GameTestHelper helper) {
        TrinityDataCoreStorageSavedData storage = new TrinityDataCoreStorageSavedData();
        TrinityExactWorkingInventory exactInventory = new TrinityExactWorkingInventory();
        ListCraftingInventory physicalInventory = new ListCraftingInventory(ignored -> {});
        UUID storageId = UUID.randomUUID();
        AEItemKey key = AEItemKey.of(Items.IRON_INGOT);
        BigInteger total = BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.TWO).add(BigInteger.valueOf(23L));
        exactInventory.deposit(key, total, physicalInventory);

        int[] transferCalls = { 0 };
        boolean transferred = exactInventory.transferAll((what, amount) -> {
            transferCalls[0]++;
            return storage.insertExact(
                    storageId,
                    what,
                    amount,
                    Actionable.MODULATE,
                    TrinityDataCoreStorageProfile.UNLIMITED);
        });

        BigInteger overflow = BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.valueOf(23L));
        helper.assertTrue(transferred, "Exact CPU overflow is cleared after the unlimited sink accepts it");
        helper.assertValueEqual(transferCalls[0], 1, "The exact overflow is sent to Trinity in one transfer");
        helper.assertValueEqual(storage.amount(storageId, key), overflow, "Trinity receives the complete exact overflow");
        helper.assertValueEqual(physicalInventory.list.get(key), Long.MAX_VALUE, "The physical CPU window remains separately owned");
        helper.succeed();
    }
}
