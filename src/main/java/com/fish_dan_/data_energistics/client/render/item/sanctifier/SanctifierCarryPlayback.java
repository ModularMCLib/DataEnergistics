package com.fish_dan_.data_energistics.client.render.item.sanctifier;

import com.fish_dan_.data_energistics.common.data.DEItems;
import com.fish_dan_.data_energistics.common.item.powered.sanctifier.SanctifierCarryState;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/** Reversible draw/return clocks keep inventory-mounted blades and hand rendering mutually exclusive. */
public final class SanctifierCarryPlayback {

    private static final Object2ObjectMap<UUID, SanctifierCarryPlayback> PLAYERS = new Object2ObjectOpenHashMap<>();
    private static @Nullable ClientLevel world;
    private SanctifierCarryState state = SanctifierCarryState.EMPTY;
    private ItemStack weapon = ItemStack.EMPTY;
    private float previous;
    private float progress;
    private boolean selected;

    private SanctifierCarryPlayback() {}

    public static void tick(ClientTickEvent.Post event) {
        var minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (world != level) {
            clear();
            world = level;
        }
        if (level == null) return;
        for (Player player : level.players()) {
            SanctifierCarryState state = player == minecraft.player ? SanctifierCarryState.fromInventory(player) : player.getMainHandItem().is(DEItems.DATA_SANCTIFIER.get()) ? SanctifierCarryState.of(player.getMainHandItem()) : SanctifierCarryState.tracked(player);
            SanctifierCarryPlayback playback = PLAYERS.get(player.getUUID());
            if (playback == null) {
                if (!state.carried()) continue;
                playback = new SanctifierCarryPlayback();
                PLAYERS.put(player.getUUID(), playback);
            }
            playback.state = state;
            playback.selected = player.getMainHandItem().is(DEItems.DATA_SANCTIFIER.get());
            if (playback.selected) playback.weapon = player.getMainHandItem().copy();
            else if (playback.weapon.isEmpty() && state.carried()) playback.weapon = DEItems.DATA_SANCTIFIER.get().getDefaultInstance();
            playback.previous = playback.progress;
            playback.progress = Mth.clamp(playback.progress + (playback.selected ? 1 : -1) / 12.0F, 0, 1);
        }
        PLAYERS.object2ObjectEntrySet().removeIf(entry -> level.getPlayerByUUID(entry.getKey()) == null || !entry.getValue().state.carried() && entry.getValue().progress == 0);
    }

    public static @Nullable SanctifierCarryPlayback find(Player player) {
        return player.level() == world ? PLAYERS.get(player.getUUID()) : null;
    }

    public SanctifierCarryState state() {
        return state;
    }

    public boolean selected() {
        return selected;
    }

    public ItemStack weapon() {
        return weapon;
    }

    public float progress(float partialTick) {
        return Mth.lerp(partialTick, previous, progress);
    }

    public boolean transitioning() {
        return selected ? previous < 1 : previous > 0;
    }

    public static void clear() {
        PLAYERS.clear();
        world = null;
    }
}
