package com.fish_dan_.data_energistics.common.item.powered.sanctifier;

import com.fish_dan_.data_energistics.common.network.packets.sanctifier.SanctifierCarryPayload;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

import java.util.UUID;

/** Sends inventory carry transitions and initial tracking snapshots from the server thread. */
public final class SanctifierCarrySync {

    private final Object2IntMap<UUID> previous = new Object2IntOpenHashMap<>();

    public SanctifierCarrySync() {
        previous.defaultReturnValue(-1);
    }

    @SubscribeEvent
    public void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        SanctifierCarryState state = SanctifierCarryState.fromInventory(player);
        if (previous.put(player.getUUID(), state.flags()) != state.flags()) {
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new SanctifierCarryPayload(player.getId(), player.getUUID(), state.flags()));
        }
    }

    @SubscribeEvent
    public void tracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer observer && event.getTarget() instanceof ServerPlayer target) {
            PacketDistributor.sendToPlayer(observer, new SanctifierCarryPayload(target.getId(), target.getUUID(), SanctifierCarryState.fromInventory(target).flags()));
        }
    }

    @SubscribeEvent
    public void login(PlayerEvent.PlayerLoggedInEvent event) {
        previous.removeInt(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        previous.removeInt(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public void respawn(PlayerEvent.PlayerRespawnEvent event) {
        previous.removeInt(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        previous.removeInt(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public void stopped(ServerStoppedEvent event) {
        previous.clear();
    }
}
