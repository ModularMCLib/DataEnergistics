package com.fish_dan_.data_energistics.world.packaged;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.crafting.packaged.execution.PackagedEntityCapture;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import java.util.UUID;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class PackagedEntityLifetimeGameTest {

    @TestHolder("packaged_owned_machine_drops_survive_provider_offline_item_expiry")
    @EmptyTemplate("9")
    @GameTest(template = "empty_9x9")
    public static void ownedDropsSurviveVanillaExpiry(GameTestHelper helper) {
        var pos = helper.absolutePos(new BlockPos(4, 3, 4));
        var owner = UUID.randomUUID();
        var owned = new ItemEntity(helper.getLevel(), pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, new ItemStack(Items.DIAMOND));
        owned.setNoGravity(true);
        owned.setDeltaMovement(Vec3.ZERO);
        PackagedEntityCapture.run(helper.getLevel(), owner, () -> helper.getLevel().addFreshEntity(owned));
        for (int tick = 0; tick < 6001; tick++) owned.tick();
        helper.assertTrue(owned.isAlive(), "An uncollected native output must not expire while its provider is offline");
        helper.assertTrue(PackagedEntityCapture.ownedBy(owned, owner), "Physical output must retain its operation ownership");
        owned.discard();
        helper.succeed();
    }
}
