package com.fish_dan_.data_energistics.menu.machine.anvil;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.blockentity.machine.anvil.MeDigitalAnvilBlockEntity;
import com.fish_dan_.data_energistics.common.anvil.DigitalAnvilUpgrade;
import com.fish_dan_.data_energistics.common.research.item.ItemResearchUpgrades;
import com.fish_dan_.data_energistics.registry.DEBlocks;
import com.fish_dan_.data_energistics.registry.DEDataComponents;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.gametest.ExtendedGameTestHelper;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class MeDigitalAnvilAdjustmentGameTest {

    private static final BlockPos ANVIL = new BlockPos(2, 2, 2);

    private MeDigitalAnvilAdjustmentGameTest() {}

    @GameTest(template = "empty_5x5")
    public static void adjustmentAmountsRespectBothLimitsAndLegacyRequests(GameTestHelper helper) {
        MeDigitalAnvilMenu menu = createMenu(helper);
        adjust(helper, menu, "boost_up", "1", 1);
        adjust(helper, menu, "boost_up", "10", 11);
        adjust(helper, menu, "boost_up", "100", 111);
        adjust(helper, menu, "boost_up", "100", 211);
        adjust(helper, menu, "boost_up", "100", 254);
        adjust(helper, menu, "boost_up", "1", 254);
        adjust(helper, menu, "boost_down", "1", 253);
        adjust(helper, menu, "boost_up", "1", 254);
        adjust(helper, menu, "boost_down", "100", 154);
        adjust(helper, menu, "boost_down", "100", 54);
        adjust(helper, menu, "boost_down", "10", 44);
        adjust(helper, menu, "boost_down", "100", 0);
        adjust(helper, menu, "boost_down", "1", 0);
        adjust(helper, menu, "boost_up", "", 1);
        for (String invalid : new String[]{"-100", "0", "2", "2147483647", "not a number"}) {
            adjust(helper, menu, "boost_up", invalid, 1);
        }
        helper.assertTrue(!menu.host().faulted(), "Invalid operation amounts must not fault the machine");
        menu.removed(menu.getPlayer());
        helper.succeed();
    }

    @GameTest(template = "empty_5x5")
    public static void removingResearchResetsAnExistingBoostAndRejectsNewAdjustments(GameTestHelper helper) {
        MeDigitalAnvilMenu menu = createMenu(helper);
        adjust(helper, menu, "boost_up", "100", 100);
        menu.host().setComponents(DataComponentMap.EMPTY);
        menu.action("boost_up", "100");
        menu.action("rename", "");
        helper.assertValueEqual(menu.snapshot().getInt("boost"), 0, "Removing research must reset the previous requested boost");
        menu.action("boost_down", "10");
        helper.assertValueEqual(menu.snapshot().getInt("boost"), 0, "Locked controls must reject operation amounts");
        menu.removed(menu.getPlayer());
        helper.succeed();
    }

    private static MeDigitalAnvilMenu createMenu(GameTestHelper helper) {
        helper.setBlock(ANVIL.below(), Blocks.STONE);
        helper.setBlock(ANVIL, DEBlocks.ME_DIGITAL_ANVIL.get());
        MeDigitalAnvilBlockEntity host = helper.getBlockEntity(ANVIL);
        host.setComponents(DataComponentMap.builder().set(DEDataComponents.RESEARCH_UPGRADES.get(),
                ItemResearchUpgrades.EMPTY.with(DigitalAnvilUpgrade.UNRESTRICTED_LEVELS.researchId())).build());
        var player = new ExtendedGameTestHelper(helper.testInfo).makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        player.setPos(helper.absolutePos(ANVIL).getCenter());
        return new MeDigitalAnvilMenu(1, player.getInventory(), host);
    }

    private static void adjust(GameTestHelper helper, MeDigitalAnvilMenu menu, String action, String amount, int expected) {
        menu.action(action, amount);
        helper.assertValueEqual(menu.snapshot().getInt("boost"), expected, "Unexpected boost for " + action + " with amount " + amount);
    }
}
