package com.fish_dan_.data_energistics.common.research.lifecycle;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.network.packets.research.ResearchSnapshotPayload;
import com.fish_dan_.data_energistics.common.research.persistence.ResearchSavedData;
import com.fish_dan_.data_energistics.common.research.synchronization.ResearchSnapshots;
import com.fish_dan_.data_energistics.common.research.team.ResearchTeams;
import com.fish_dan_.data_energistics.data.research.ResearchReloadListener;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Handles player synchronization and server-side research work. */
@EventBusSubscriber(modid = Data_Energistics.MOD_ID)
public final class ResearchEvents {

    private ResearchEvents() {}

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) sendCatalog(player);
    }

    @SubscribeEvent
    public static void datapackSync(OnDatapackSyncEvent event) {
        if (event.getPlayer() != null) sendCatalog(event.getPlayer());
        else event.getPlayerList().getPlayers().forEach(ResearchEvents::sendCatalog);
    }

    private static void sendCatalog(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new ResearchSnapshotPayload(-1, ResearchSnapshots.catalog(player)));
    }

    public static void addReloadListener(AddReloadListenerEvent event) {
        event.addListener(new ResearchReloadListener(event.getRegistryAccess()));
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        ResearchTeams.onLogout(event);
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        ResearchSavedData.get(event.getServer()).tickMaterialCalculations(event.getServer());
    }

    public static void stopping(ServerStoppingEvent event) {
        ResearchSavedData.get(event.getServer()).cancelMaterialCalculations();
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        ResearchTeams.clearSessions(event);
    }
}
