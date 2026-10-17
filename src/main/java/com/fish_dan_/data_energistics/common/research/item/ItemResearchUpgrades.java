package com.fish_dan_.data_energistics.common.research.item;

import com.fish_dan_.data_energistics.registry.DEDataComponents;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectLists;

/** Immutable, synchronized upgrades carried by this item rather than by its current owner. */
public record ItemResearchUpgrades(ObjectList<ResourceLocation> ids) {

    public static final ItemResearchUpgrades EMPTY = new ItemResearchUpgrades(ObjectLists.emptyList());
    public static final Codec<ItemResearchUpgrades> CODEC = ResourceLocation.CODEC.listOf().xmap(
            list -> new ItemResearchUpgrades(new ObjectImmutableList<>(list)), ItemResearchUpgrades::ids);
    public static final StreamCodec<RegistryFriendlyByteBuf, ItemResearchUpgrades> STREAM_CODEC = StreamCodec.of(
            (buffer, value) -> {
                buffer.writeVarInt(value.ids.size());
                value.ids.forEach(buffer::writeResourceLocation);
            }, buffer -> {
                int count = buffer.readVarInt();
                if (count < 0 || count > 4096) throw new IllegalArgumentException("Invalid item upgrade count: " + count);
                ResourceLocation[] ids = new ResourceLocation[count];
                for (int i = 0; i < count; i++) ids[i] = buffer.readResourceLocation();
                return new ItemResearchUpgrades(ObjectImmutableList.of(ids));
            });

    public ItemResearchUpgrades {
        ids = new ObjectImmutableList<>(new ObjectLinkedOpenHashSet<>(ids));
        if (ids.size() > 4096) throw new IllegalArgumentException("Too many item research upgrades");
    }

    public static ItemResearchUpgrades of(ItemStack stack) {
        return stack.getOrDefault(DEDataComponents.RESEARCH_UPGRADES.get(), EMPTY);
    }

    public ItemResearchUpgrades with(ResourceLocation id) {
        var updated = new ObjectLinkedOpenHashSet<>(ids);
        updated.add(id);
        return new ItemResearchUpgrades(new ObjectImmutableList<>(updated));
    }
}
