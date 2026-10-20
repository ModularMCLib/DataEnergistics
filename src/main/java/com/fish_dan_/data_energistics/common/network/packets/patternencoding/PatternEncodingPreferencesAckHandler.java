package com.fish_dan_.data_energistics.common.network.packets.patternencoding;

import com.fish_dan_.data_energistics.client.preferences.PatternEncodingPreferencesAckClientHandler;

import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

/**
 * Routes preference acknowledgements to the client handler without running them on a server.
 */
public final class PatternEncodingPreferencesAckHandler {

    private PatternEncodingPreferencesAckHandler() {}

    /**
     * Dispatches one acknowledgement only on a physical client.
     */
    public static void handle(PatternEncodingPreferencesAckPayload payload, Player player) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }
        PatternEncodingPreferencesAckClientHandler.handle(payload, player);
    }
}
