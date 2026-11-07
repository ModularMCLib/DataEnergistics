package com.fish_dan_.data_energistics.common.weapon.platform.storage;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import it.unimi.dsi.fastutil.objects.ObjectSets;

/** Effect identifiers the platform prevents from remaining active on its wearer. */
public record SuppressedEffects(ObjectSet<ResourceLocation> suppressed) {

    public static final SuppressedEffects DEFAULT = new SuppressedEffects(ObjectSets.emptySet());
    public static final Codec<SuppressedEffects> CODEC = ResourceLocation.CODEC.listOf()
            .xmap(ObjectOpenHashSet::new, ids -> ids.stream().sorted().toList())
            .xmap(SuppressedEffects::new, settings -> new ObjectOpenHashSet<>(settings.suppressed()))
            .fieldOf("suppressed").codec();
    public static final StreamCodec<RegistryFriendlyByteBuf, SuppressedEffects> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);

    public SuppressedEffects {
        suppressed = ObjectSets.unmodifiable(new ObjectOpenHashSet<>(suppressed));
    }

    public boolean suppresses(ResourceLocation effect) {
        return suppressed.contains(effect);
    }

    public SuppressedEffects toggle(ResourceLocation effect) {
        var updated = new ObjectOpenHashSet<>(suppressed);
        if (!updated.add(effect)) updated.remove(effect);
        return new SuppressedEffects(updated);
    }
}
