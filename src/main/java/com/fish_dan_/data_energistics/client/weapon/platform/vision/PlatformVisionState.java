package com.fish_dan_.data_energistics.client.weapon.platform.vision;

import com.fish_dan_.data_energistics.client.weapon.platform.PlatformClientState;
import com.fish_dan_.data_energistics.common.weapon.platform.model.PlatformAbility;

import net.minecraft.client.Minecraft;

/** Render-thread view of the server's paid capability flags. Requested switches never grant visual effects. */
public final class PlatformVisionState {

    private PlatformVisionState() {}

    public static boolean active(PlatformAbility ability) {
        return (PlatformClientState.snapshot.systems().active() & ability.flag) != 0;
    }

    public static float lightStrength() {
        if (active(PlatformAbility.NIGHT_VISION)) return 1;
        if (!active(PlatformAbility.LOW_LIGHT_VISION)) return 0;
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) return 0;
        return 0.35f * client.level.getMaxLocalRawBrightness(client.player.blockPosition()) / 15f;
    }
}
