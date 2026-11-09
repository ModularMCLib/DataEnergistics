package com.fish_dan_.data_energistics.common.weapon.platform.combat;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Dispersed follow stations behind the wearer, relative to body yaw rather than camera pitch. */
public final class GunFormation {

    private GunFormation() {}

    public static Vec3 offset(Player player, int slot) {
        int row = slot / 2;
        double side = (slot % 2 == 0 ? -1 : 1) * (3.2 + row * .9);
        return new Vec3(side, player.getEyeHeight() + .9 + row % 2 * .55, -1.8 - row * 2.3)
                .yRot((float) Math.toRadians(-player.yBodyRot));
    }
}
