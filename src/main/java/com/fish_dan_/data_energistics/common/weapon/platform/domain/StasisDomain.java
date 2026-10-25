package com.fish_dan_.data_energistics.common.weapon.platform.domain;

import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.UUID;

/** Fixed chunk-aligned volume. The overworld game clock continues while entities inside it are stopped. */
public record StasisDomain(UUID owner, ResourceKey<Level> dimension, int x, int z, int size, long expires) {

    public static final Codec<StasisDomain> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("owner").forGetter(StasisDomain::owner),
            Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(StasisDomain::dimension),
            Codec.INT.fieldOf("x").forGetter(StasisDomain::x), Codec.INT.fieldOf("z").forGetter(StasisDomain::z),
            Codec.intRange(3, 5).fieldOf("size").forGetter(StasisDomain::size),
            Codec.LONG.fieldOf("expires").forGetter(StasisDomain::expires)).apply(instance, StasisDomain::new));

    public boolean contains(int chunkX, int chunkZ) {
        return Math.abs((long) chunkX - x) <= size / 2 && Math.abs((long) chunkZ - z) <= size / 2;
    }
}
