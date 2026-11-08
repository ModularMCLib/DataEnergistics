package com.fish_dan_.data_energistics.client.event;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.client.hud.orbital.OrbitalControlHudClientState;
import com.fish_dan_.data_energistics.client.hud.weapon_platform.information.TacticalPlaytime;
import com.fish_dan_.data_energistics.client.hud.weapon_platform.rendering.TacticalProjection;
import com.fish_dan_.data_energistics.client.preferences.PatternUploadSucceededClientHandler;
import com.fish_dan_.data_energistics.client.render.item.blade.BladeAttackPresentation;
import com.fish_dan_.data_energistics.client.render.item.blade.animation.BladeAttackPlayback;
import com.fish_dan_.data_energistics.client.render.item.sanctifier.SanctifierCarryPlayback;
import com.fish_dan_.data_energistics.client.render.item.sanctifier.SanctifierHandPresentation;
import com.fish_dan_.data_energistics.client.render.orbital.OrbitalAttackVisualClientState;
import com.fish_dan_.data_energistics.client.render.orbital.OrbitalProjectionVisualClientState;
import com.fish_dan_.data_energistics.client.render.orbital.OrbitalWorldProjectionRenderer;
import com.fish_dan_.data_energistics.client.research.ResearchClientEvents;
import com.fish_dan_.data_energistics.client.screen.patternencoding.PatternEncodingUploadClient;
import com.fish_dan_.data_energistics.client.terminal.TerminalOverlayClient;
import com.fish_dan_.data_energistics.client.terminal.rendering.TerminalCarriedItemRenderer;
import com.fish_dan_.data_energistics.client.terminal.rendering.TerminalTooltipRenderer;
import com.fish_dan_.data_energistics.client.ui.orbital.OrbitalControlClientBindings;
import com.fish_dan_.data_energistics.client.weapon.platform.DomainClientState;
import com.fish_dan_.data_energistics.client.weapon.platform.PlatformClientState;
import com.fish_dan_.data_energistics.client.weapon.platform.PlatformFlightMiningClient;
import com.fish_dan_.data_energistics.common.menu.patternencoding.PatternEncodingPreferenceSession;
import com.fish_dan_.data_energistics.config.client.ClientConfigurations;
import com.fish_dan_.data_energistics.utils.StableDigest;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.Locale;

public final class ClientGameEventRegistrar {

    private ClientGameEventRegistrar() {}

    public static void register() {
        OrbitalControlClientBindings.install();
        NeoForge.EVENT_BUS.register(new ResearchClientEvents());
        NeoForge.EVENT_BUS.register(new PlatformFlightMiningClient());
        NeoForge.EVENT_BUS.register(TerminalOverlayClient.class);
        NeoForge.EVENT_BUS.register(PatternEncodingUploadClient.class);
        NeoForge.EVENT_BUS.register(TerminalCarriedItemRenderer.class);
        // Equal-priority foreground passes run in registration order: tooltips follow windows and the cursor.
        NeoForge.EVENT_BUS.register(TerminalTooltipRenderer.class);
        NeoForge.EVENT_BUS.addListener(ClientInputHandler::onInteractionKeyTriggered);
        NeoForge.EVENT_BUS.addListener(ClientInputHandler::onMouseButtonPre);
        NeoForge.EVENT_BUS.addListener(ClientInputHandler::onKeyInput);
        NeoForge.EVENT_BUS.addListener(ClientTickHandler::onClientTickPost);
        NeoForge.EVENT_BUS.addListener(TacticalProjection::capture);
        NeoForge.EVENT_BUS.addListener(SanctifierHandPresentation::renderHand);
        NeoForge.EVENT_BUS.addListener(SanctifierHandPresentation::preparePlayerPose);
        NeoForge.EVENT_BUS.addListener(BladeAttackPresentation::renderHand);
        NeoForge.EVENT_BUS.addListener(BladeAttackPlayback::tick);
        NeoForge.EVENT_BUS.addListener(SanctifierCarryPlayback::tick);
        NeoForge.EVENT_BUS.addListener(ClientInputHandler::onMouseScroll);
        NeoForge.EVENT_BUS.addListener(ClientScreenEventHandler::onScreenOpening);
        NeoForge.EVENT_BUS.addListener(ClientScreenEventHandler::onScreenInitPost);
        NeoForge.EVENT_BUS.addListener(ClientScreenEventHandler::onScreenRenderPost);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, ClientScreenEventHandler::onContainerForeground);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, ClientScreenEventHandler::onRenderTooltip);
        NeoForge.EVENT_BUS.addListener(ClientGameEventRegistrar::onLoggingIn);
        NeoForge.EVENT_BUS.addListener(ClientGameEventRegistrar::onLoggingOut);
    }

    private static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        Minecraft minecraft = Minecraft.getInstance();
        var preferences = ClientConfigurations.PATTERN_ENCODING_PREFERENCES;
        var integratedServer = minecraft.getSingleplayerServer();
        if (integratedServer != null) {
            var worldPath = integratedServer.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
            String profile = StableDigest.sha256("singleplayer\0" + worldPath);
            TacticalPlaytime.connected(profile);
            preferences.activateServerProfile(profile);
            return;
        }
        var serverData = minecraft.getCurrentServer();
        if (serverData != null && ServerAddress.isValidAddress(serverData.ip.trim())) {
            ServerAddress address = ServerAddress.parseString(serverData.ip.trim());
            String host = address.getHost().toLowerCase(Locale.ROOT);
            if (!host.isBlank()) {
                String profile = StableDigest.sha256("remote\0" + host + '\0' + address.getPort());
                TacticalPlaytime.connected(profile);
                preferences.activateServerProfile(profile);
                return;
            }
        }
        TacticalPlaytime.connected(StableDigest.sha256("remote\0" + event.getConnection().getRemoteAddress()));
        Data_Energistics.LOGGER.warn("Unable to identify the current server; pattern provider statistics will remain session-only");
        preferences.deactivateServerProfile();
    }

    private static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        TacticalPlaytime.disconnected();
        PlatformClientState.clear();
        DomainClientState.clear();
        BladeAttackPlayback.clear();
        SanctifierCarryPlayback.clear();
        if (event.getPlayer() != null) {
            PatternEncodingPreferenceSession.clearForMenu(event.getPlayer().containerMenu);
        }
        PatternUploadSucceededClientHandler.clear();
        TerminalOverlayClient.clear();
        PatternEncodingUploadClient.clear();
        OrbitalControlClientBindings.clear();
        OrbitalAttackVisualClientState.clear();
        OrbitalProjectionVisualClientState.clear();
        OrbitalWorldProjectionRenderer.releaseBuffers();
        OrbitalControlHudClientState.clear();
        ClientConfigurations.PATTERN_ENCODING_PREFERENCES.deactivateServerProfile();
    }
}
