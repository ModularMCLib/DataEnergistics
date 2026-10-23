package com.fish_dan_.data_energistics.common.entity.projectile;

import com.fish_dan_.data_energistics.common.entity.projectile.cannon.CannonShot;

import net.minecraft.world.item.ItemStack;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Typed persisted state for a thrown saber and its captured crossbow shot. */
public record ThrownLightSaberState(CannonShot shot, double traveledDistance, boolean dealtDamage,
                                    boolean homing, int energyCards, ItemStack weapon) {

    public static final Codec<ThrownLightSaberState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            CannonShot.CODEC.fieldOf("shot").forGetter(ThrownLightSaberState::shot),
            Codec.DOUBLE.validate(value -> Double.isFinite(value) && value >= 0 ? DataResult.success(value) : DataResult.error(() -> "Invalid saber travel distance"))
                    .fieldOf("traveled_distance").forGetter(ThrownLightSaberState::traveledDistance),
            Codec.BOOL.fieldOf("dealt_damage").forGetter(ThrownLightSaberState::dealtDamage),
            Codec.BOOL.fieldOf("homing").forGetter(ThrownLightSaberState::homing),
            Codec.intRange(0, Integer.MAX_VALUE).fieldOf("energy_cards").forGetter(ThrownLightSaberState::energyCards),
            ItemStack.OPTIONAL_CODEC.fieldOf("weapon").forGetter(ThrownLightSaberState::weapon))
            .apply(instance, ThrownLightSaberState::new));
}
