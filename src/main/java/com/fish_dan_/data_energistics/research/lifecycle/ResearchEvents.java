package com.fish_dan_.data_energistics.research.lifecycle;

import com.fish_dan_.data_energistics.network.research.ResearchSnapshotPayload;
import com.fish_dan_.data_energistics.research.definition.ResearchReloadListener;
import com.fish_dan_.data_energistics.research.synchronization.ResearchSnapshots;
import com.fish_dan_.data_energistics.research.team.ResearchTeams;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Explicit game-bus entry points; definitions, memberships and execution remain in their own domains. */
public final class ResearchEvents {

    @SubscribeEvent
    public void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) sendCatalog(player);
    }

    @SubscribeEvent
    public void datapackSync(OnDatapackSyncEvent event) {
        if (event.getPlayer() != null) sendCatalog(event.getPlayer());
        else event.getPlayerList().getPlayers().forEach(ResearchEvents::sendCatalog);
    }

    private static void sendCatalog(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new ResearchSnapshotPayload(-1, ResearchSnapshots.catalog(player)));
    }

    @SubscribeEvent
    public void addReloadListener(AddReloadListenerEvent event) {
        event.addListener(new ResearchReloadListener(event.getRegistryAccess()));
    }

    @SubscribeEvent
    public void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        ResearchTeams.onLogout(event);
    }

    @SubscribeEvent
    public void stopped(ServerStoppedEvent event) {
        ResearchTeams.clearSessions(event);
    }
}
