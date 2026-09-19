package com.fish_dan_.data_energistics.ae2.patternprovider.packaged;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.blockentity.patternprovider.DigitalPackagedPatternProviderBlockEntity;
import com.fish_dan_.data_energistics.menu.patternprovider.DigitalPackagedPatternProviderMenu;
import com.fish_dan_.data_energistics.registry.DEBlocks;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.helpers.patternprovider.PatternProviderReturnInventory;
import appeng.menu.SlotSemantics;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class PackagedReturnInventoryGameTest {

    private PackagedReturnInventoryGameTest() {}

    @TestHolder("packaged_provider_menu_exposes_both_return_rows_with_long_counts")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void providerMenuExposesAllEighteenReturnSlots(GameTestHelper helper) {
        var position = new BlockPos(1, 1, 1);
        helper.setBlock(position, DEBlocks.DIGITAL_PACKAGED_PATTERN_PROVIDER.get());
        var provider = (DigitalPackagedPatternProviderBlockEntity) helper.getBlockEntity(position);
        var inventory = provider.getLogic().getReturnInv();
        var key = AEItemKey.of(Items.IRON_INGOT);
        inventory.insert(17, key, Long.MAX_VALUE, Actionable.MODULATE);
        var menu = new DigitalPackagedPatternProviderMenu(1, helper.makeMockPlayer(GameType.SURVIVAL).getInventory(), provider);
        helper.assertTrue(menu.getSlots(SlotSemantics.ENCODED_PATTERN).size() == 36,
                "Standalone provider must expose exactly thirty-six pattern slots");
        var slots = menu.getSlots(SlotSemantics.STORAGE);
        helper.assertTrue(slots.size() == 18, "Both return rows must be accessible in the actual provider menu");
        var stack = GenericStack.unwrapItemStack(slots.get(17).getItem());
        helper.assertTrue(stack != null && stack.what().equals(key) && stack.amount() == Long.MAX_VALUE,
                "The last visible return slot must retain its full long quantity");
        inventory.clear();
        helper.succeed();
    }

    @TestHolder("packaged_return_inventory_keeps_eighteen_long_slots_across_reload_and_menu_sync")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void eighteenLongSlotsSurviveReloadAndMenuSync(GameTestHelper helper) {
        int standardSlots = PatternProviderReturnInventory.NUMBER_OF_SLOTS;
        var inventory = new PackagedReturnInventory(() -> {});
        helper.assertTrue(inventory.size() == 18, "Packaged return inventory must contain both rows");
        helper.assertTrue(new PatternProviderReturnInventory(() -> {}).size() == standardSlots,
                "Constructing the packaged inventory must not resize ordinary providers");
        var key = AEItemKey.of(Items.IRON_INGOT);
        for (int slot = 0; slot < 18; slot++) {
            helper.assertTrue(inventory.insert(slot, key, Long.MAX_VALUE - 1, Actionable.MODULATE) == Long.MAX_VALUE - 1,
                    "Every slot must accept long quantities");
        }
        helper.assertTrue(inventory.insert(17, key, Long.MAX_VALUE, Actionable.SIMULATE) == 1,
                "Near-full simulation must use remaining capacity without overflow");
        helper.assertTrue(inventory.getAmount(17) == Long.MAX_VALUE - 1, "Simulation must not mutate the buffer");
        helper.assertTrue(inventory.insert(17, key, Long.MAX_VALUE, Actionable.MODULATE) == 1,
                "Actual insertion must match simulated remaining capacity");
        var restored = new PackagedReturnInventory(() -> {});
        restored.readFromTag(inventory.writeToTag(helper.getLevel().registryAccess()), helper.getLevel().registryAccess());
        var menu = restored.createMenuWrapper();
        for (int slot = 0; slot < 18; slot++) {
            long expected = slot == 17 ? Long.MAX_VALUE : Long.MAX_VALUE - 1;
            var wrapped = GenericStack.unwrapItemStack(menu.getStackInSlot(slot));
            helper.assertTrue(wrapped != null && wrapped.what().equals(key) && wrapped.amount() == expected,
                    "NBT and menu synchronization must retain the exact long amount in both rows");
            helper.assertTrue(wrapped.equals(menu.convertToSuitableStack(menu.getStackInSlot(slot))),
                    "Menu conversion must preserve wrapped resource quantities");
        }
        helper.assertTrue(restored.insert(key, Long.MAX_VALUE, Actionable.MODULATE, IActionSource.empty()) == 17,
                "Whole-inventory insertion must fill only the seventeen remaining units");
        helper.assertTrue(restored.insert(key, 1, Actionable.MODULATE, IActionSource.empty()) == 0,
                "A full buffer must reject additional returns for the operation to retain");
        helper.succeed();
    }
}
