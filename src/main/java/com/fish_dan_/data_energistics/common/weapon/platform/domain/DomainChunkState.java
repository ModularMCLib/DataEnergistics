package com.fish_dan_.data_energistics.common.weapon.platform.domain;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;

public record DomainChunkState(ResourceKey<Level> dimension, long chunk, ObjectList<FrozenChunkTicks> ticks) {

    public static final Codec<DomainChunkState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(DomainChunkState::dimension), Codec.LONG.fieldOf("chunk").forGetter(DomainChunkState::chunk),
            FrozenChunkTicks.CODEC.listOf().<ObjectList<FrozenChunkTicks>>xmap(ObjectArrayList::new, values -> values).fieldOf("ticks").forGetter(DomainChunkState::ticks)).apply(instance, DomainChunkState::new));
}
