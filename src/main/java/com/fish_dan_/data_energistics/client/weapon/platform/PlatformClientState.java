package com.fish_dan_.data_energistics.client.weapon.platform;

import com.fish_dan_.data_energistics.common.weapon.platform.protocol.PlatformSnapshot;

/** Client-thread snapshot shared by menu-independent HUD controls. Cleared on disconnect and equipment loss. */
public final class PlatformClientState {

    public static PlatformSnapshot snapshot = PlatformSnapshot.EMPTY;

    private PlatformClientState() {}

    public static void accept(PlatformSnapshot value) {
        snapshot = value;
    }
}
