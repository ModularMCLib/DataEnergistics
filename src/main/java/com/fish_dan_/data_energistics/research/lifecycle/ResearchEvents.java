package com.fish_dan_.data_energistics.research.lifecycle;

import com.fish_dan_.data_energistics.research.definition.ResearchReloadListener;
import com.fish_dan_.data_energistics.research.team.ResearchTeams;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/** Explicit game-bus entry points; definitions, memberships and execution remain in their own domains. */
public final class ResearchEvents {

    @SubscribeEvent
    public void addReloadListener(AddReloadListenerEvent event) {
        event.addListener(new ResearchReloadListener(event.getRegistryAccess()));
    }

    @SubscribeEvent
    public void registerCommands(RegisterCommandsEvent event) {
        ResearchTeams.registerCommands(event);
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
