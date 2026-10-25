package com.fish_dan_.data_energistics.util.input;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Client-thread callback for a complete chord transition. Register during client setup; temporary
 * listeners must be removed when their owner closes. Releases also occur on focus/session loss.
 */
@OnlyIn(Dist.CLIENT)
@FunctionalInterface
public interface ClientKeyListener {

    /** Receives a non-null client and binding; player/level may be absent during release cleanup. */
    void onKeyPressed(Minecraft client, SyncedKeyMapping key, boolean down);
}
