package com.fish_dan_.data_energistics.common.entity.projectile.cannon;

import net.minecraft.core.UUIDUtil;
import net.minecraft.world.item.ItemStack;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Optional;
import java.util.UUID;

/** Complete persisted bolt snapshot; entity NBT callbacks only encode and restore this typed boundary. */
public record CannonBoltState(CannonShot shot, Optional<RailShot> railShot, boolean modernEffects,
                              float fragmentDamage, int singularityTicks, double traveledDistance,
                              int color, int pierceLevel, int consumedPierceCount, boolean homing,
                              boolean critical, Optional<UUID> homingTarget, int energyCards, ItemStack weapon) {

    public static final Codec<CannonBoltState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            CannonShot.CODEC.fieldOf("shot").forGetter(CannonBoltState::shot),
            RailShot.CODEC.optionalFieldOf("rail_shot").forGetter(CannonBoltState::railShot),
            Codec.BOOL.fieldOf("modern_effects").forGetter(CannonBoltState::modernEffects),
            Codec.floatRange(0, Float.MAX_VALUE).fieldOf("fragment_damage").forGetter(CannonBoltState::fragmentDamage),
            Codec.intRange(0, 10).fieldOf("singularity_ticks").forGetter(CannonBoltState::singularityTicks),
            Codec.doubleRange(0, Double.MAX_VALUE).fieldOf("distance").forGetter(CannonBoltState::traveledDistance),
            Codec.INT.fieldOf("color").forGetter(CannonBoltState::color),
            Codec.intRange(0, Integer.MAX_VALUE).fieldOf("pierce_level").forGetter(CannonBoltState::pierceLevel),
            Codec.intRange(0, Integer.MAX_VALUE).fieldOf("consumed_pierce").forGetter(CannonBoltState::consumedPierceCount),
            Codec.BOOL.fieldOf("homing").forGetter(CannonBoltState::homing),
            Codec.BOOL.fieldOf("critical").forGetter(CannonBoltState::critical),
            UUIDUtil.CODEC.optionalFieldOf("homing_target").forGetter(CannonBoltState::homingTarget),
            Codec.intRange(0, Integer.MAX_VALUE).fieldOf("energy_cards").forGetter(CannonBoltState::energyCards),
            ItemStack.OPTIONAL_CODEC.fieldOf("weapon").forGetter(CannonBoltState::weapon)).apply(instance, CannonBoltState::new));
}
