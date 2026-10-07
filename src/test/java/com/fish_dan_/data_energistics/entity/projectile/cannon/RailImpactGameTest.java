package com.fish_dan_.data_energistics.entity.projectile.cannon;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import java.util.List;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class RailImpactGameTest {

    private RailImpactGameTest() {}

    @TestHolder("rail_fe_chain_orders_targets_from_impact_outward")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void feChainOrdersTargetsFromImpactOutward(GameTestHelper helper) {
        Vec3 impact = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 1, 2)));
        Mob near = helper.spawn(EntityType.IRON_GOLEM, new BlockPos(3, 1, 2));
        Mob middle = helper.spawn(EntityType.IRON_GOLEM, new BlockPos(4, 1, 2));
        Mob far = helper.spawn(EntityType.IRON_GOLEM, new BlockPos(4, 1, 4));

        List<LivingEntity> ordered = RailImpact.orderTargets(impact, List.of(far, near, middle), 3);
        helper.assertTrue(ordered.get(0) == near && ordered.get(1) == middle && ordered.get(2) == far,
                "FE targets must be ordered by distance from the impact point");
        helper.succeed();
    }
}
