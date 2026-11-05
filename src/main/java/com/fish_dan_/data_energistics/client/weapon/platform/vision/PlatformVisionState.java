package com.fish_dan_.data_energistics.client.weapon.platform.vision;

import com.fish_dan_.data_energistics.client.weapon.platform.PlatformClientState;
import com.fish_dan_.data_energistics.common.weapon.platform.model.PlatformAbility;

/** Render-thread view of the server's paid capability flags. Requested switches never grant visual effects. */
public final class PlatformVisionState {

    private PlatformVisionState() {}

    public static boolean active(PlatformAbility ability) {
        return (PlatformClientState.snapshot.systems().active() & ability.flag) != 0;
    }

    public static float lightStrength() {
        if (active(PlatformAbility.NIGHT_VISION)) return 1;
        return active(PlatformAbility.LOW_LIGHT_VISION) ? 0.35f : 0;
    }
}
