package com.fish_dan_.data_energistics.common.weapon.platform.prospecting;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;

public record OreSurveyDelta(boolean reset, ObjectList<OreSection> sections, LongList removed) {

    public static final Codec<OreSurveyDelta> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.fieldOf("reset").forGetter(OreSurveyDelta::reset),
            OreSection.CODEC.listOf(0, 16).<ObjectList<OreSection>>xmap(ObjectArrayList::new, values -> values).fieldOf("sections").forGetter(OreSurveyDelta::sections),
            Codec.LONG.listOf().<LongList>xmap(LongArrayList::new, values -> values).fieldOf("removed").forGetter(OreSurveyDelta::removed)).apply(instance, OreSurveyDelta::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, OreSurveyDelta> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);
}
