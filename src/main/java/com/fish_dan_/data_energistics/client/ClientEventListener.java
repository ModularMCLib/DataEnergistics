package com.fish_dan_.data_energistics.client;

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
import com.fish_dan_.data_energistics.client.screen.patternencoding.PatternEncodingUploadClient;
import com.fish_dan_.data_energistics.client.terminal.TerminalOverlayClient;
import com.fish_dan_.data_energistics.client.ui.orbital.OrbitalControlClientBindings;
import com.fish_dan_.data_energistics.client.weapon.platform.DomainClientState;
import com.fish_dan_.data_energistics.client.weapon.platform.PlatformClientState;
import com.fish_dan_.data_energistics.common.menu.patternencoding.PatternEncodingPreferenceSession;
import com.fish_dan_.data_energistics.config.client.ClientConfigurations;
import com.fish_dan_.data_energistics.utils.StableDigest;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;

import java.util.Locale;

@EventBusSubscriber(modid = Data_Energistics.MOD_ID, value = Dist.CLIENT)
public final class ClientEventListener {

    private ClientEventListener() {}

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        TacticalProjection.capture(event);
    }

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        SanctifierHandPresentation.renderHand(event);
        if (!event.isCanceled()) {
            BladeAttackPresentation.renderHand(event);
        }
    }

    @SubscribeEvent
    public static void onRenderPlayer(RenderPlayerEvent.Pre event) {
        SanctifierHandPresentation.preparePlayerPose(event);
    }

    @SubscribeEvent
    public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
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

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
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
