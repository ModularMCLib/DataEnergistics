package com.fish_dan_.data_energistics.common.weapon.platform.domain;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record FrozenChunkTicks(BlockPos position, ResourceLocation type, boolean fluid, long delay, int priority, long order) {

    public static final Codec<FrozenChunkTicks> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BlockPos.CODEC.fieldOf("position").forGetter(FrozenChunkTicks::position), ResourceLocation.CODEC.fieldOf("type").forGetter(FrozenChunkTicks::type),
            Codec.BOOL.fieldOf("fluid").forGetter(FrozenChunkTicks::fluid), Codec.LONG.validate(value -> value >= 0 ? DataResult.success(value) : DataResult.error(() -> "Negative frozen tick delay")).fieldOf("delay").forGetter(FrozenChunkTicks::delay),
            Codec.intRange(-3, 3).fieldOf("priority").forGetter(FrozenChunkTicks::priority), Codec.LONG.fieldOf("order").forGetter(FrozenChunkTicks::order)).apply(instance, FrozenChunkTicks::new));
}
