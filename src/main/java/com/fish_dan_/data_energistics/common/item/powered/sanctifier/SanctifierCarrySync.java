package com.fish_dan_.data_energistics.common.item.powered.sanctifier;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.network.packets.sanctifier.SanctifierCarryPayload;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

import java.util.UUID;

/** Sends inventory carry transitions and initial tracking snapshots from the server thread. */
@EventBusSubscriber(modid = Data_Energistics.MOD_ID)
public final class SanctifierCarrySync {

    private static final Object2IntMap<UUID> PREVIOUS = new Object2IntOpenHashMap<>();

    static {
        PREVIOUS.defaultReturnValue(-1);
    }

    private SanctifierCarrySync() {}

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        SanctifierCarryState state = SanctifierCarryState.fromInventory(player);
        if (PREVIOUS.put(player.getUUID(), state.flags()) != state.flags()) {
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new SanctifierCarryPayload(player.getId(), player.getUUID(), state.flags()));
        }
    }

    @SubscribeEvent
    public static void tracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer observer && event.getTarget() instanceof ServerPlayer target) {
            PacketDistributor.sendToPlayer(observer, new SanctifierCarryPayload(target.getId(), target.getUUID(), SanctifierCarryState.fromInventory(target).flags()));
        }
    }

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        PREVIOUS.removeInt(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        PREVIOUS.removeInt(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void respawn(PlayerEvent.PlayerRespawnEvent event) {
        PREVIOUS.removeInt(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        PREVIOUS.removeInt(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        PREVIOUS.clear();
    }
}
