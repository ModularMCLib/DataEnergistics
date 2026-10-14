package com.fish_dan_.data_energistics.api.research;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import it.unimi.dsi.fastutil.objects.ObjectSets;

/** Explicit item IDs or item tags to which an upgrade may be applied. Empty targets match no item. */
public record ResearchTarget(ObjectSet<ResourceLocation> items, ObjectSet<ResourceLocation> tags) {

    public static final ResearchTarget NONE = new ResearchTarget(ObjectSets.emptySet(), ObjectSets.emptySet());

    public ResearchTarget {
        items = ObjectSets.unmodifiable(new ObjectOpenHashSet<>(items));
        tags = ObjectSets.unmodifiable(new ObjectOpenHashSet<>(tags));
    }

    public static ResearchTarget item(ResourceLocation id) {
        return new ResearchTarget(ObjectSets.singleton(id), ObjectSets.emptySet());
    }

    public boolean matches(ItemStack stack) {
        return !stack.isEmpty() && (items.contains(BuiltInRegistries.ITEM.getKey(stack.getItem())) ||
                tags.stream().anyMatch(id -> stack.is(TagKey.create(Registries.ITEM, id))));
    }
}
