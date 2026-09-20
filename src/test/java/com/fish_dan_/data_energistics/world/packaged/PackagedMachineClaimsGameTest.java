package com.fish_dan_.data_energistics.world.packaged;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import it.unimi.dsi.fastutil.objects.ObjectList;

import java.util.UUID;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class PackagedMachineClaimsGameTest {

    private PackagedMachineClaimsGameTest() {}

    @TestHolder("packaged_overlapping_altars_cannot_partially_reserve_shared_pedestals")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void overlappingStructuresReserveAllPartsOrNone(GameTestHelper helper) {
        var claims = new PackagedMachineClaims();
        var first = UUID.randomUUID();
        var second = UUID.randomUUID();
        var firstCore = new BlockPos(0, 1, 0);
        var secondCore = new BlockPos(2, 1, 0);
        var sharedPedestal = new BlockPos(1, 1, 0);
        var firstParts = ObjectList.of(firstCore, sharedPedestal);
        var secondParts = ObjectList.of(secondCore, sharedPedestal);
        helper.assertTrue(claims.acquireAll(firstParts, first), "First machine must reserve its full structure");
        helper.assertTrue(!claims.acquireAll(secondParts, second), "Another machine must not share an occupied pedestal");
        helper.assertTrue(claims.available(secondCore), "A rejected reservation must not retain a partial core claim");
        helper.assertTrue(claims.acquireAll(firstParts, first), "The owning task must be able to revalidate its reservation");
        claims.releaseAll(firstParts, first);
        helper.assertTrue(claims.acquireAll(secondParts, second), "Releasing a finished structure must allow the waiting machine");
        helper.succeed();
    }
}
