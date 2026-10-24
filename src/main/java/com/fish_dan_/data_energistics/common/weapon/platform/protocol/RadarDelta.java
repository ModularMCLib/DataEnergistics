package com.fish_dan_.data_energistics.common.weapon.platform.protocol;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;

/** Bounded transport batches do not limit the total number of radar targets. */
public record RadarDelta(boolean reset, ObjectList<TargetSnapshot> changed, IntList removed) {

    public static final Codec<RadarDelta> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.fieldOf("reset").forGetter(RadarDelta::reset),
            TargetSnapshot.CODEC.listOf(0, 128).<ObjectList<TargetSnapshot>>xmap(ObjectArrayList::new, values -> values).fieldOf("changed").forGetter(RadarDelta::changed),
            Codec.INT.listOf().<IntList>xmap(IntArrayList::new, values -> values).fieldOf("removed").forGetter(RadarDelta::removed)).apply(instance, RadarDelta::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, RadarDelta> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);
}
