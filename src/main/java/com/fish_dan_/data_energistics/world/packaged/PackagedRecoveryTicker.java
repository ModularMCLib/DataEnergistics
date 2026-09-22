package com.fish_dan_.data_energistics.world.packaged;

import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/** Finishes physical recovery even when the detached provider's voucher has been lost. */
public final class PackagedRecoveryTicker {

    @SubscribeEvent
    public void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level && level.getGameTime() % 20 == 0) {
            PackagedRecoveryStore.get(level).tick(level);
        }
    }
}
