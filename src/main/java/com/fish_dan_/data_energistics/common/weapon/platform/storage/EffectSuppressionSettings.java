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
public record EffectSuppressionSettings(ObjectSet<ResourceLocation> suppressed) {

    public static final EffectSuppressionSettings DEFAULT = new EffectSuppressionSettings(ObjectSets.emptySet());
    public static final Codec<EffectSuppressionSettings> CODEC = ResourceLocation.CODEC.listOf()
            .xmap(ObjectOpenHashSet::new, ids -> ids.stream().sorted().toList())
            .xmap(EffectSuppressionSettings::new, settings -> new ObjectOpenHashSet<>(settings.suppressed()))
            .fieldOf("suppressed").codec();
    public static final StreamCodec<RegistryFriendlyByteBuf, EffectSuppressionSettings> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);

    public EffectSuppressionSettings {
        suppressed = ObjectSets.unmodifiable(new ObjectOpenHashSet<>(suppressed));
    }

    public boolean suppresses(ResourceLocation effect) {
        return suppressed.contains(effect);
    }

    public EffectSuppressionSettings toggle(ResourceLocation effect) {
        var updated = new ObjectOpenHashSet<>(suppressed);
        if (!updated.add(effect)) updated.remove(effect);
        return new EffectSuppressionSettings(updated);
    }
}
