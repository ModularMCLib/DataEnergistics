package com.fish_dan_.data_energistics.common.world.packaged;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/** Finishes physical recovery even when the detached provider's voucher has been lost. */
@EventBusSubscriber(modid = Data_Energistics.MOD_ID)
public final class PackagedRecoveryTicker {

    private PackagedRecoveryTicker() {}

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level && level.getGameTime() % 20 == 0) {
            PackagedRecoveryStore.get(level).tick(level);
        }
    }
}
