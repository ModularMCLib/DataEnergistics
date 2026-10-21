package com.fish_dan_.data_energistics.common.research.item;

import com.fish_dan_.data_energistics.common.data.DEDataComponents;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMaps;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.jspecify.annotations.Nullable;

/** Immutable item-owned record of mutually exclusive routes already chosen on this stack. */
public record ItemResearchExclusions(Object2ObjectMap<ResourceLocation, ResourceLocation> winners) {

    public static final ItemResearchExclusions EMPTY = new ItemResearchExclusions(Object2ObjectMaps.emptyMap());
    public static final Codec<ItemResearchExclusions> CODEC = Codec.unboundedMap(ResourceLocation.CODEC, ResourceLocation.CODEC)
            .xmap(values -> new ItemResearchExclusions(new Object2ObjectOpenHashMap<>(values)),
                    ItemResearchExclusions::winners);
    public static final StreamCodec<RegistryFriendlyByteBuf, ItemResearchExclusions> STREAM_CODEC = StreamCodec.of(
            (buffer, value) -> {
                buffer.writeVarInt(value.winners.size());
                value.winners.forEach((blocked, winner) -> {
                    buffer.writeResourceLocation(blocked);
                    buffer.writeResourceLocation(winner);
                });
            }, buffer -> {
                int count = buffer.readVarInt();
                if (count < 0 || count > 4096) throw new IllegalArgumentException("Invalid item research exclusion count: " + count);
                Object2ObjectMap<ResourceLocation, ResourceLocation> winners = new Object2ObjectOpenHashMap<>();
                for (int i = 0; i < count; i++) {
                    ResourceLocation blocked = buffer.readResourceLocation();
                    ResourceLocation winner = buffer.readResourceLocation();
                    if (winners.putIfAbsent(blocked, winner) != null) {
                        throw new IllegalArgumentException("Duplicate item research exclusion: " + blocked);
                    }
                }
                return new ItemResearchExclusions(winners);
            });

    public ItemResearchExclusions {
        winners = Object2ObjectMaps.unmodifiable(new Object2ObjectOpenHashMap<>(winners));
        if (winners.size() > 4096) throw new IllegalArgumentException("Too many item research exclusions");
    }

    public static ItemResearchExclusions of(ItemStack stack) {
        return stack.getOrDefault(DEDataComponents.RESEARCH_EXCLUSIONS.get(), EMPTY);
    }

    public @Nullable ResourceLocation winner(ResourceLocation blocked) {
        return winners.get(blocked);
    }

    public ItemResearchExclusions with(ResourceLocation blocked, ResourceLocation winner) {
        Object2ObjectMap<ResourceLocation, ResourceLocation> updated = new Object2ObjectOpenHashMap<>(winners);
        updated.putIfAbsent(blocked, winner);
        return new ItemResearchExclusions(updated);
    }
}
