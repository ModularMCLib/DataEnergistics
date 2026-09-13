package com.fish_dan_.data_energistics.client.render.overlay.connector;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class ConnectorLinkGeometryGameTest {

    private ConnectorLinkGeometryGameTest() {}

    @TestHolder("connector_link_all_six_faces_are_planar_and_reached_from_outside")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void allSixFacesArePlanarAndReachedFromOutside(GameTestHelper helper) {
        BlockPos position = new BlockPos(-19, 70, 23);
        for (Direction side : Direction.values()) {
            var face = ConnectorLinkGeometry.face(position, side);
            Vec3 normal = Vec3.atLowerCornerOf(side.getNormal());
            helper.assertTrue(face.center().subtract(Vec3.atCenterOf(position)).dot(normal) > 0.5D,
                    "Marker must sit outside the selected block face: " + side);
            helper.assertTrue(face.approach().subtract(face.center()).normalize().distanceTo(normal) < 1.0E-9D,
                    "The final link segment must follow the target face normal: " + side);
            for (int index = 0; index < face.corners().size(); index++) {
                Vec3 corner = face.corners().get(index);
                Vec3 next = face.corners().get((index + 1) % face.corners().size());
                helper.assertTrue(Math.abs(corner.subtract(face.center()).dot(normal)) < 1.0E-9D,
                        "Every outline corner must lie in the bound face plane: " + side);
                helper.assertTrue(Math.abs(corner.distanceTo(next) - 0.6D) < 1.0E-9D,
                        "Each face must be a closed square, including top and bottom: " + side);
            }
        }
        helper.succeed();
    }
}
