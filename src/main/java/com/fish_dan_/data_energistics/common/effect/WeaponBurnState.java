package com.fish_dan_.data_energistics.common.effect;

import net.minecraft.core.UUIDUtil;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Optional;
import java.util.UUID;

/** Immutable timed-burn attachment; cadence survives refreshes and persisted entity reloads. */
public record WeaponBurnState(int remaining, int cadence, float damage, boolean afterburn,
                              boolean decayToZero, int decayDuration, Optional<UUID> owner) {

    public static final WeaponBurnState EMPTY = new WeaponBurnState(0, 0, 0, false, false, 0, Optional.empty());
    public static final Codec<WeaponBurnState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.intRange(0, Integer.MAX_VALUE).fieldOf("remaining").forGetter(WeaponBurnState::remaining),
            Codec.intRange(0, 19).fieldOf("cadence").forGetter(WeaponBurnState::cadence),
            Codec.floatRange(0, Float.MAX_VALUE).fieldOf("damage").forGetter(WeaponBurnState::damage),
            Codec.BOOL.fieldOf("afterburn").forGetter(WeaponBurnState::afterburn),
            Codec.BOOL.fieldOf("decay").forGetter(WeaponBurnState::decayToZero),
            Codec.intRange(0, Integer.MAX_VALUE).fieldOf("decay_duration").forGetter(WeaponBurnState::decayDuration),
            UUIDUtil.CODEC.optionalFieldOf("owner").forGetter(WeaponBurnState::owner)).apply(instance, WeaponBurnState::new));
}
