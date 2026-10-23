package com.fish_dan_.data_energistics.common.weapon.platform.runtime;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.weapon.platform.equipment.EquippedWeaponPlatform;
import com.fish_dan_.data_energistics.common.weapon.platform.network.PlatformRpc;
import com.fish_dan_.data_energistics.common.weapon.platform.protocol.PlatformSnapshot;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/** Equipment lifecycle and a single failure boundary; invalid state pauses only the affected platform. */
public final class WeaponPlatformTicker {

    private static final Object2ObjectOpenHashMap<UUID, PlatformRuntime> PLAYERS = new Object2ObjectOpenHashMap<>();

    public static @Nullable PlatformRuntime runtime(ServerPlayer player) {
        var platform = EquippedWeaponPlatform.find(player);
        if (!player.isAlive() || player.isSpectator() || platform.isEmpty()) {
            PLAYERS.remove(player.getUUID());
            return null;
        }
        PlatformRuntime current = PLAYERS.get(player.getUUID());
        if (current == null || current.player != player || current.platform != platform) {
            current = new PlatformRuntime(player, platform);
            PLAYERS.put(player.getUUID(), current);
        }
        return current;
    }

    @SubscribeEvent
    public void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        boolean equipped = PLAYERS.containsKey(player.getUUID());
        PlatformRuntime runtime = runtime(player);
        if (runtime == null) {
            if (equipped) PlatformRpc.push(player, PlatformSnapshot.EMPTY);
            return;
        }
        try {
            if (!runtime.failed) runtime.tick();
            if (player.tickCount % 5 == 0) PlatformRpc.push(player, PlatformSnapshot.capture(runtime));
        } catch (RuntimeException failure) {
            runtime.failed = true;
            Data_Energistics.LOGGER.error("Weapon platform paused for player {} in {}", player.getUUID(), player.level().dimension().location(), failure);
        }
    }

    @SubscribeEvent
    public void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        PLAYERS.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public void stopped(ServerStoppedEvent event) {
        PLAYERS.clear();
    }
}
