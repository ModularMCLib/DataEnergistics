package com.fish_dan_.data_energistics.client.render.item.blade.animation;

import com.fish_dan_.data_energistics.client.render.item.blade.effect.BladeSlashRenderer;
import com.fish_dan_.data_energistics.common.item.powered.blade.BladeSwing;
import com.fish_dan_.data_energistics.common.item.powered.blade.BladeWeapon;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/** Both camera perspectives sample the same server-accepted swings. */
public final class BladeAttackPlayback {

    private static final Int2ObjectOpenHashMap<BladeAttackPlayback> SWINGS = new Int2ObjectOpenHashMap<>();
    private static @Nullable ClientLevel world;

    private final Player player;
    private final BladeWeapon weapon;
    private final BladeSwing swing;
    private final int started;
    private final @Nullable Matrix4f precedingPose;
    private boolean impacted;

    private BladeAttackPlayback(Player player, BladeWeapon weapon, BladeSwing swing, @Nullable Matrix4f precedingPose) {
        this.player = player;
        this.weapon = weapon;
        this.swing = swing;
        this.started = player.tickCount;
        this.precedingPose = precedingPose;
    }

    public static void begin(Player player, BladeWeapon weapon, BladeSwing swing) {
        ClientLevel level = (ClientLevel) player.level();
        if (world != level) {
            clear();
            world = level;
        }
        BladeAttackPlayback previous = active(player);
        Matrix4f precedingPose = previous == null ? null : previous.sample(player.tickCount - previous.started);
        SWINGS.put(player.getId(), new BladeAttackPlayback(player, weapon, swing, precedingPose));
    }

    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (world != minecraft.level) {
            clear();
            world = minecraft.level;
        }
        BladeSlashRenderer.tick();
        if (SWINGS.isEmpty()) return;
        var entries = Int2ObjectMaps.fastIterator(SWINGS);
        while (entries.hasNext()) {
            BladeAttackPlayback playback = entries.next().getValue();
            Player player = playback.player;
            int elapsed = player.tickCount - playback.started;
            if (!player.isAlive() || player.isUsingItem() || elapsed >= playback.swing.duration()) {
                entries.remove();
            } else if (!playback.impacted && elapsed >= playback.swing.impactTick()) {
                playback.impacted = true;
                if (playback.weapon.matches(player.getMainHandItem())) {
                    BladeSlashRenderer.start(player, playback.weapon, playback.swing);
                    if (player == minecraft.player) player.resetAttackStrengthTicker();
                }
            }
        }
    }

    public static boolean isSwinging(Player player) {
        return active(player) != null;
    }

    public static @Nullable Matrix4f motion(LivingEntity player, float partialTick) {
        if (player.isUsingItem()) return null;
        BladeAttackPlayback playback = active(player);
        if (playback != null) return playback.sample(player.tickCount - playback.started + partialTick);
        // Vanilla still owns block mining and supplies its main-hand swing.
        if (player.swinging && player.swingingArm == InteractionHand.MAIN_HAND) {
            BladeWeapon weapon = BladeWeapon.of(player.getMainHandItem());
            if (weapon != null) {
                return BladeAttackMotion.sample(weapon, BladeSwing.FOREHAND, player.getAttackAnim(partialTick) * BladeSwing.FOREHAND.duration());
            }
        }
        return null;
    }

    private static @Nullable BladeAttackPlayback active(LivingEntity player) {
        BladeAttackPlayback playback = SWINGS.get(player.getId());
        return playback != null && playback.player == player && playback.weapon.matches(player.getMainHandItem()) ? playback : null;
    }

    private @Nullable Matrix4f sample(float elapsed) {
        if (elapsed >= swing.duration()) return null;
        Matrix4f motion = BladeAttackMotion.sample(weapon, swing, elapsed);
        float windup = swing.impactTick() - 1F;
        if (precedingPose == null || elapsed >= windup) return motion;

        // A quick follow-up starts from the previous grip and joins the new backswing before the cut.
        float blend = elapsed / windup;
        Vector3f position = precedingPose.getTranslation(new Vector3f()).lerp(motion.getTranslation(new Vector3f()), blend);
        Quaternionf rotation = precedingPose.getNormalizedRotation(new Quaternionf()).slerp(motion.getNormalizedRotation(new Quaternionf()), blend);
        return motion.translation(position).rotate(rotation);
    }

    public static void clear() {
        SWINGS.clear();
        BladeSlashRenderer.clear();
        world = null;
    }
}
