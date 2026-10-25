package com.fish_dan_.data_energistics.common.weapon.platform.domain;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Native block events, including piston actions, stay ordered and world-persistent while covered. */
public record DeferredBlockEvent(ResourceKey<Level> dimension, BlockPos position, ResourceLocation block, int action, int parameter) {

    public static final Codec<DeferredBlockEvent> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(DeferredBlockEvent::dimension),
            BlockPos.CODEC.fieldOf("position").forGetter(DeferredBlockEvent::position),
            ResourceLocation.CODEC.fieldOf("block").forGetter(DeferredBlockEvent::block),
            Codec.INT.fieldOf("action").forGetter(DeferredBlockEvent::action),
            Codec.INT.fieldOf("parameter").forGetter(DeferredBlockEvent::parameter)).apply(instance, DeferredBlockEvent::new));
}
