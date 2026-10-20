package com.fish_dan_.data_energistics.item.powered.sanctifier;

import com.fish_dan_.data_energistics.common.network.packets.sanctifier.SanctifierCarryPayload;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.WeakHashMap;

/** Sends carry changes and initial tracking snapshots; unchanged hotbars produce no packets. */
public final class SanctifierCarrySync {

    private final WeakHashMap<ServerPlayer, SanctifierCarryState> previous = new WeakHashMap<>();

    @SubscribeEvent
    public void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        SanctifierCarryState state = SanctifierCarryState.fromHotbar(player);
        SanctifierCarryState before = this.previous.put(player, state);
        if (!state.equals(before)) {
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(player,
                    new SanctifierCarryPayload(player.getId(), state));
        }
    }

    @SubscribeEvent
    public void tracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer observer && event.getTarget() instanceof ServerPlayer target) {
            PacketDistributor.sendToPlayer(observer,
                    new SanctifierCarryPayload(target.getId(), SanctifierCarryState.fromHotbar(target)));
        }
    }

    @SubscribeEvent
    public void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) this.previous.remove(player);
    }
}
