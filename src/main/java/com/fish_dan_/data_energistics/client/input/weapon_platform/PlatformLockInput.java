package com.fish_dan_.data_energistics.client.input.weapon_platform;

import com.fish_dan_.data_energistics.client.registry.DEKeyMappings;
import com.fish_dan_.data_energistics.client.weapon.platform.PlatformClientState;
import com.fish_dan_.data_energistics.common.weapon.platform.network.PlatformRpc;

import com.lowdragmc.lowdraglib2.networking.rpc.RPCPacketDistributor;

import net.minecraft.client.Minecraft;

/** A short release toggles one target; a held binding starts an additive server-side lock sweep. */
public final class PlatformLockInput {

    private static final int HOLD_TICKS = 4;
    private static int heldTicks;
    private static boolean wasDown;

    private PlatformLockInput() {}

    public static void tick(Minecraft minecraft) {
        var binding = DEKeyMappings.LOCK_WEAPON_PLATFORM_TARGET;
        boolean clicked = false;
        while (binding.consumeClick()) clicked = true;
        boolean down = binding.isDown();
        boolean connected = minecraft.player != null && minecraft.level != null && minecraft.getConnection() != null;
        boolean available = connected && minecraft.screen == null && minecraft.isWindowActive() && !minecraft.isPaused() && minecraft.player.isAlive() && !minecraft.player.isSpectator() && PlatformClientState.snapshot.present();
        if (!available) {
            if (connected && heldTicks >= HOLD_TICKS) RPCPacketDistributor.rpcToServer(PlatformRpc.REQUEST, PlatformRpc.LOCK, 0, PlatformRpc.LOCK_SWEEP_END);
            heldTicks = 0;
            wasDown = down;
            return;
        }
        if (down) {
            if (!wasDown) heldTicks = 1;
            else if (heldTicks > 0 && heldTicks < HOLD_TICKS && ++heldTicks == HOLD_TICKS) {
                RPCPacketDistributor.rpcToServer(PlatformRpc.REQUEST, PlatformRpc.LOCK, 0, PlatformRpc.LOCK_SWEEP_START);
            }
        } else {
            if (heldTicks >= HOLD_TICKS) RPCPacketDistributor.rpcToServer(PlatformRpc.REQUEST, PlatformRpc.LOCK, 0, PlatformRpc.LOCK_SWEEP_END);
            else if (heldTicks > 0 || clicked && !wasDown) RPCPacketDistributor.rpcToServer(PlatformRpc.REQUEST, PlatformRpc.LOCK, 0, PlatformRpc.LOCK_TOGGLE);
            heldTicks = 0;
        }
        wasDown = down;
    }
}
