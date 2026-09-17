package com.fish_dan_.data_energistics.client.sound.crossbow;

import com.fish_dan_.data_energistics.client.render.item.crossbow.CrossbowAnimation;
import com.fish_dan_.data_energistics.item.powered.MatterConvergingCrossbowMode;
import com.fish_dan_.data_energistics.item.powered.cannon.ammunition.RailAmmunition;
import com.fish_dan_.data_energistics.registry.DESounds;

import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** Client-thread audio for one hand. Instances do not retain their owning entity. */
@NullMarked
public final class CrossbowSounds {

    private @Nullable SoundInstance chargeSound;
    private @Nullable SoundInstance recoverySound;
    private boolean charging;
    private boolean returning;
    private boolean recovering;
    private MatterConvergingCrossbowMode mode = MatterConvergingCrossbowMode.GRENADE;

    /** Observes the updated animation once per client tick; a new shot replaces the preceding recovery sound. */
    public void tick(SoundManager sounds, LivingEntity entity, CrossbowAnimation animation,
                     boolean using, float progress, boolean fired, int duration) {
        if (!animation.held() || animation.mode() != this.mode) {
            stop(sounds);
        }
        this.mode = animation.mode();
        boolean charging = animation.held() && using && progress < 1.0F;
        if (charging && !this.charging) {
            this.chargeSound = play(sounds, entity, DESounds.STAR_SHARD_CHARGE.get());
        } else if (!charging && this.chargeSound != null) {
            sounds.stop(this.chargeSound);
            this.chargeSound = null;
        }
        this.charging = charging;

        boolean returning = animation.recoilReturnStarted();
        this.recovering = animation.recoilActive() && (fired || this.recovering);
        boolean heavy = this.mode == MatterConvergingCrossbowMode.RAIL && duration == RailAmmunition.HEAVY.cooldownTicks();
        if (this.recovering && (fired || returning != this.returning)) {
            if (this.recoverySound != null) sounds.stop(this.recoverySound);
            SoundEvent sound;
            if (this.mode == MatterConvergingCrossbowMode.CROSSBOW) {
                sound = returning ? DESounds.CROSSBOW_EQUIP.get() : DESounds.CROSSBOW_EXHAUST.get();
            } else if (heavy) {
                sound = returning ? DESounds.HEAVY_RAIL_EQUIP.get() : DESounds.HEAVY_RAIL_EXHAUST.get();
            } else {
                sound = returning ? DESounds.STAR_SHARD_EQUIP.get() : DESounds.STAR_SHARD_EXHAUST.get();
            }
            this.recoverySound = play(sounds, entity, sound);
        } else if (!animation.recoilActive() && this.recoverySound != null) {
            sounds.stop(this.recoverySound);
            this.recoverySound = null;
        }
        this.returning = returning;
    }

    /** Stops both stages when changing slots, leaving the level, or dropping the weapon. */
    public void stop(SoundManager sounds) {
        if (this.chargeSound != null) sounds.stop(this.chargeSound);
        if (this.recoverySound != null) sounds.stop(this.recoverySound);
        this.chargeSound = null;
        this.recoverySound = null;
        this.charging = false;
        this.returning = false;
        this.recovering = false;
    }

    private static SoundInstance play(SoundManager sounds, LivingEntity entity, SoundEvent event) {
        var sound = new SimpleSoundInstance(event, SoundSource.PLAYERS, 0.8F, 1.0F,
                SoundInstance.createUnseededRandom(), entity.getX(), entity.getY(), entity.getZ());
        sounds.play(sound);
        return sound;
    }
}
