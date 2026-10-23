package com.fish_dan_.data_energistics.common.weapon.platform.protocol;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.world.phys.Vec3;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Includes geometry and a name for distant targets outside the client's normal entity tracking range. */
public record TargetSnapshot(int entity, float health, float maximum, int binary, int color, Component name, Vec3 minimum, Vec3 maximumPosition, boolean visible) {

    public static final Codec<TargetSnapshot> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("entity").forGetter(TargetSnapshot::entity),
            Codec.FLOAT.fieldOf("health").forGetter(TargetSnapshot::health),
            Codec.FLOAT.fieldOf("maximum").forGetter(TargetSnapshot::maximum),
            Codec.intRange(0, 32).fieldOf("binary").forGetter(TargetSnapshot::binary),
            Codec.INT.fieldOf("color").forGetter(TargetSnapshot::color),
            ComponentSerialization.CODEC.fieldOf("name").forGetter(TargetSnapshot::name),
            Vec3.CODEC.fieldOf("minimum").forGetter(TargetSnapshot::minimum),
            Vec3.CODEC.fieldOf("maximum_position").forGetter(TargetSnapshot::maximumPosition),
            Codec.BOOL.fieldOf("visible").forGetter(TargetSnapshot::visible)).apply(instance, TargetSnapshot::new));
}
