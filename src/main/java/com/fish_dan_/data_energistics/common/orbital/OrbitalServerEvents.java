package com.fish_dan_.data_energistics.common.orbital;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.orbital.attack.OrbitalAttackSavedData;
import com.fish_dan_.data_energistics.common.orbital.attack.OrbitalVisualPublisher;
import com.fish_dan_.data_energistics.common.orbital.control.OrbitalControlActionDispatcher;
import com.fish_dan_.data_energistics.common.orbital.control.OrbitalControlHudPublisher;
import com.fish_dan_.data_energistics.common.orbital.control.OrbitalOwnershipActionDispatcher;
import com.fish_dan_.data_energistics.common.orbital.storage.StellarErasureDeviceSavedData;

import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Runs the orbital simulation and publishes its resulting state on the logical server. */
@EventBusSubscriber(modid = Data_Energistics.MOD_ID)
public final class OrbitalServerEvents {

    private static final OrbitalControlHudPublisher HUD = new OrbitalControlHudPublisher();

    private OrbitalServerEvents() {}

    @SubscribeEvent
    public static void onServerTickPost(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        StellarErasureDeviceSavedData.get(server).chargeReserves(server);
        OrbitalAttackSavedData.get(server).tick(server);
        OrbitalVisualPublisher.publish(server);
        HUD.publish(server);
        OrbitalControlActionDispatcher.expirePreviews(server);
        OrbitalOwnershipActionDispatcher.expire(server);
    }

    public static void onServerStopping(ServerStoppingEvent event) {
        OrbitalAttackSavedData.get(event.getServer()).releaseRuntimeResources(event.getServer());
    }
}
