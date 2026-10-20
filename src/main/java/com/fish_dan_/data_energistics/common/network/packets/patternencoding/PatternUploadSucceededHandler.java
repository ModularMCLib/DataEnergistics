package com.fish_dan_.data_energistics.common.network.packets.patternencoding;

import com.fish_dan_.data_energistics.client.preferences.PatternUploadSucceededClientHandler;

import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

/**
 * Routes confirmed upload events to the client handler while keeping common network registration server-safe.
 */
public final class PatternUploadSucceededHandler {

    private PatternUploadSucceededHandler() {}

    /**
     * Dispatches one confirmed upload only on a physical client.
     */
    public static void handle(PatternUploadSucceededPayload payload, Player player) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }
        PatternUploadSucceededClientHandler.handle(payload, player);
    }
}
