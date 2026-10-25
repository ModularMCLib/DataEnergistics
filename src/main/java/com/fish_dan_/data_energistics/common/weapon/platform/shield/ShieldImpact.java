package com.fish_dan_.data_energistics.common.weapon.platform.shield;

import net.minecraft.world.phys.Vec3;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Transient hit direction relative to the shield center, with server game time for fading. */
public record ShieldImpact(Vec3 direction, long at) {

    public static final Codec<ShieldImpact> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Vec3.CODEC.fieldOf("direction").forGetter(ShieldImpact::direction),
            Codec.LONG.fieldOf("at").forGetter(ShieldImpact::at)).apply(instance, ShieldImpact::new));
}
