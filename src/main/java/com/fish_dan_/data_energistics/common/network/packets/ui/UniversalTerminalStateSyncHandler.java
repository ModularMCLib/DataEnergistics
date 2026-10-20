package com.fish_dan_.data_energistics.common.network.packets.ui;

import com.fish_dan_.data_energistics.client.screen.terminal.UniversalTerminalStateSyncClientHandler;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

public final class UniversalTerminalStateSyncHandler {

    private UniversalTerminalStateSyncHandler() {}

    public static void cacheSyncedTerminalState(UniversalTerminalStateSyncPayload payload) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }

        UniversalTerminalStateSyncClientHandler.cacheSyncedTerminalState(payload);
    }
}
