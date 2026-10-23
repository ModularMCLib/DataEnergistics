package com.fish_dan_.data_energistics.client.weapon.platform;

import com.fish_dan_.data_energistics.client.hud.weapon_platform.TacticalHudInteractionScreen;
import com.fish_dan_.data_energistics.client.hud.weapon_platform.TacticalHudLayer;
import com.fish_dan_.data_energistics.common.weapon.platform.protocol.PlatformSnapshot;

import net.minecraft.client.Minecraft;

/** Client-thread snapshot shared by menu-independent HUD controls. Cleared on disconnect and equipment loss. */
public final class PlatformClientState {

    public static PlatformSnapshot snapshot = PlatformSnapshot.EMPTY;

    private PlatformClientState() {}

    public static void accept(PlatformSnapshot value) {
        snapshot = value;
    }

    public static void openAppearance() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || !snapshot.hud()) return;
        client.player.closeContainer();
        TacticalHudLayer.openAppearance();
        client.setScreen(new TacticalHudInteractionScreen());
    }
}
