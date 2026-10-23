package com.fish_dan_.data_energistics.common.weapon.platform.protocol;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Relation and binary layers are authoritative; clients obtain interpolated geometry from the entity ID. */
public record TargetSnapshot(int entity, float health, float maximum, int binary, int color) {

    public static final Codec<TargetSnapshot> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("entity").forGetter(TargetSnapshot::entity),
            Codec.FLOAT.fieldOf("health").forGetter(TargetSnapshot::health),
            Codec.FLOAT.fieldOf("maximum").forGetter(TargetSnapshot::maximum),
            Codec.intRange(0, 32).fieldOf("binary").forGetter(TargetSnapshot::binary),
            Codec.INT.fieldOf("color").forGetter(TargetSnapshot::color)).apply(instance, TargetSnapshot::new));
}
