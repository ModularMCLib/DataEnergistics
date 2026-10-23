package com.fish_dan_.data_energistics.client.render.item.blade.animation;

import com.fish_dan_.data_energistics.client.render.item.sanctifier.SanctifierHandPresentation;
import com.fish_dan_.data_energistics.common.data.DEItems;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/** Client-world attack clocks driven by vanilla swing synchronization, shared by both camera perspectives. */
public final class BladeAttackPlayback {

    private static final Object2ObjectMap<UUID, BladeAttackPlayback> PLAYERS = new Object2ObjectOpenHashMap<>();
    private static @Nullable ClientLevel world;
    private final Item item;
    private int combo = -1;
    private int previousSwingTime;
    private boolean previouslySwinging;
    private long started = Long.MIN_VALUE;

    private BladeAttackPlayback(Item item) {
        this.item = item;
    }

    public static void tick(ClientTickEvent.Post event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (world != level) {
            clear();
            world = level;
        }
        if (level == null) return;
        for (Player player : level.players()) {
            ItemStack stack = player.getMainHandItem();
            if (!stack.is(DEItems.DATA_SANCTIFIER.get()) && !stack.is(DEItems.DATA_LIGHT_SABER.get())) {
                PLAYERS.remove(player.getUUID());
                continue;
            }
            BladeAttackPlayback playback = PLAYERS.get(player.getUUID());
            if (playback == null || playback.item != stack.getItem()) {
                playback = new BladeAttackPlayback(stack.getItem());
                PLAYERS.put(player.getUUID(), playback);
            }
            boolean swinging = player.swinging && player.swingingArm == InteractionHand.MAIN_HAND;
            if (swinging && (!playback.previouslySwinging || player.swingTime < playback.previousSwingTime) && SanctifierHandPresentation.execution(player) == null) {
                long now = level.getGameTime();
                playback.combo = playback.combo < 0 || now - playback.started > 30 ? 0 : (playback.combo + 1) % 3;
                playback.started = now;
            }
            playback.previouslySwinging = swinging;
            playback.previousSwingTime = player.swingTime;
        }
        PLAYERS.object2ObjectEntrySet().removeIf(entry -> level.getPlayerByUUID(entry.getKey()) == null);
    }

    public static @Nullable Matrix4f motion(LivingEntity player, float partialTick) {
        if (player.level() != world) return null;
        BladeAttackPlayback playback = PLAYERS.get(player.getUUID());
        if (playback == null || playback.combo < 0 || playback.item != player.getMainHandItem().getItem()) return null;
        float elapsed = player.level().getGameTime() - playback.started + partialTick;
        return elapsed < 0 || elapsed >= BladeAttackMotion.DURATION_TICKS ? null : BladeAttackMotion.sample(playback.combo, elapsed / 20);
    }

    public static void clear() {
        PLAYERS.clear();
        world = null;
    }
}
