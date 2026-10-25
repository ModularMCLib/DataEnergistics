package com.fish_dan_.data_energistics.common.weapon.platform.domain;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;

import java.util.UUID;

public record DomainVisualSnapshot(ObjectList<StasisDomain> domains, ObjectSet<UUID> exemptPlayers) {

    public static final Codec<DomainVisualSnapshot> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            StasisDomain.CODEC.listOf().<ObjectList<StasisDomain>>xmap(ObjectArrayList::new, values -> values).fieldOf("domains").forGetter(DomainVisualSnapshot::domains),
            UUIDUtil.CODEC.listOf().<ObjectSet<UUID>>xmap(ObjectOpenHashSet::new, ObjectArrayList::new).fieldOf("exempt_players").forGetter(DomainVisualSnapshot::exemptPlayers)).apply(instance, DomainVisualSnapshot::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, DomainVisualSnapshot> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);
}
