package com.fish_dan_.data_energistics.client.input.weapon_platform;

import com.fish_dan_.data_energistics.client.weapon.platform.PlatformClientState;
import com.fish_dan_.data_energistics.common.weapon.platform.network.PlatformRpc;
import com.fish_dan_.data_energistics.util.input.SyncedKeyMappings;
import com.fish_dan_.data_energistics.util.input.SyncedKeyMapping;

import com.lowdragmc.lowdraglib2.networking.rpc.RPCPacketDistributor;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** A short release toggles one target; a held binding starts an additive server-side lock sweep. */
public final class PlatformLockInput {

    private static final int HOLD_TICKS = 4;
    private static int heldTicks;
    private static boolean removing;

    private PlatformLockInput() {}

    public static void changed(Minecraft minecraft, SyncedKeyMapping key, boolean down) {
        if (down) {
            if (available(minecraft)) heldTicks = 1;
            return;
        }
        boolean connected = minecraft.player != null && minecraft.getConnection() != null;
        if (connected && heldTicks >= HOLD_TICKS) request(PlatformRpc.LOCK_SWEEP_END);
        else if (heldTicks > 0 && available(minecraft)) request(PlatformRpc.LOCK_TOGGLE);
        heldTicks = 0;
    }

    private static boolean available(Minecraft minecraft) {
        boolean connected = minecraft.player != null && minecraft.level != null && minecraft.getConnection() != null;
        return connected && minecraft.screen == null && minecraft.isWindowActive() && !minecraft.isPaused() && minecraft.player.isAlive() && !minecraft.player.isSpectator() && PlatformClientState.snapshot.present();
    }

    public static void tick(Minecraft minecraft) {
        if (!available(minecraft)) {
            if (minecraft.getConnection() != null && heldTicks >= HOLD_TICKS) request(PlatformRpc.LOCK_SWEEP_END);
            heldTicks = 0;
            return;
        }
        if (!SyncedKeyMappings.TARGET_LOCK.isKeyDown()) return;
        if (heldTicks > 0 && heldTicks < HOLD_TICKS && ++heldTicks == HOLD_TICKS || heldTicks >= HOLD_TICKS && removing != Screen.hasControlDown()) {
            removing = Screen.hasControlDown();
            request(removing ? PlatformRpc.LOCK_SWEEP_REMOVE : PlatformRpc.LOCK_SWEEP_START);
        }
    }

    private static void request(int action) {
        RPCPacketDistributor.rpcToServer(PlatformRpc.REQUEST, PlatformRpc.LOCK, 0, action);
    }
}
