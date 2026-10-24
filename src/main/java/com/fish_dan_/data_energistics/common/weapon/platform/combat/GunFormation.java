package com.fish_dan_.data_energistics.common.weapon.platform.combat;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Shared firing and render positions, relative to the wearer's body rather than camera pitch. */
public final class GunFormation {

    private GunFormation() {}

    public static Vec3 offset(Player player, int slot) {
        double side = (slot % 2 == 0 ? -1 : 1) * (0.85 + slot / 4 * 0.3);
        return new Vec3(side, player.getEyeHeight() + 0.2 + slot / 2 * 0.16, 0.25 + slot / 2 * 0.24)
                .yRot((float) Math.toRadians(-player.yBodyRot));
    }
}
