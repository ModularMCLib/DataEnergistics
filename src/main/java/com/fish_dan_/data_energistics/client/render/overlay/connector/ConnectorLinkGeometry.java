package com.fish_dan_.data_energistics.client.render.overlay.connector;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** World-space endpoints shared by the connector face outline and its incoming link. */
public final class ConnectorLinkGeometry {

    private ConnectorLinkGeometry() {}

    /** Places the marker just outside the selected face, so the link ends on that face rather than inside the block. */
    public static Face face(BlockPos position, Direction side) {
        Vec3 normal = Vec3.atLowerCornerOf(side.getNormal());
        Vec3 center = Vec3.atCenterOf(position).add(normal.scale(0.5125D));
        Vec3 horizontal = side.getAxis() == Direction.Axis.X ? new Vec3(0, 0, 0.3D) : new Vec3(0.3D, 0, 0);
        Vec3 vertical = side.getAxis() == Direction.Axis.Y ? new Vec3(0, 0, 0.3D) : new Vec3(0, 0.3D, 0);
        return new Face(center, center.add(normal.scale(0.3D)), List.of(
                center.subtract(horizontal).subtract(vertical), center.add(horizontal).subtract(vertical),
                center.add(horizontal).add(vertical), center.subtract(horizontal).add(vertical)));
    }

    /** Four ordered corners form the outline; approach keeps the final segment perpendicular to the selected face. */
    public record Face(Vec3 center, Vec3 approach, List<Vec3> corners) {}
}
