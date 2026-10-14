package com.fish_dan_.data_energistics.research.persistence;

import com.fish_dan_.data_energistics.api.research.ResearchInput;
import com.fish_dan_.data_energistics.api.research.ResearchNode;
import com.fish_dan_.data_energistics.api.research.ResearchScope;
import com.fish_dan_.data_energistics.api.research.ResearchTarget;

import appeng.api.stacks.AEKey;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;

/** Shared world/wire representation; toolkit graphs and class names never enter authoritative data. */
public final class ResearchNbt {

    private ResearchNbt() {}

    public static ListTag ids(Iterable<ResourceLocation> values) {
        ListTag result = new ListTag();
        for (ResourceLocation id : values) result.add(StringTag.valueOf(id.toString()));
        return result;
    }

    public static ObjectSet<ResourceLocation> readIds(CompoundTag tag, String field) {
        ObjectSet<ResourceLocation> result = new ObjectOpenHashSet<>();
        for (Tag value : tag.getList(field, Tag.TAG_STRING)) result.add(ResourceLocation.parse(value.getAsString()));
        return result;
    }

    public static CompoundTag input(ResearchInput input, HolderLookup.Provider registries) {
        CompoundTag result = new CompoundTag();
        result.put("key", input.key().toTagGeneric(registries));
        result.putLong("amount", input.amount());
        return result;
    }

    public static ResearchInput readInput(CompoundTag tag, HolderLookup.Provider registries) {
        AEKey key = AEKey.fromTagGeneric(registries, tag.getCompound("key"));
        if (key == null) throw new IllegalArgumentException("Unknown AE key in research input");
        return new ResearchInput(key, tag.getLong("amount"));
    }

    public static CompoundTag node(ResearchNode node, HolderLookup.Provider registries) {
        CompoundTag result = new CompoundTag();
        result.putString("id", node.id().toString());
        result.putString("scope", node.scope().name());
        result.putString("name", node.name());
        result.putString("description", node.description());
        result.putString("icon", node.icon().toString());
        result.put("target_items", ids(node.target().items()));
        result.put("target_tags", ids(node.target().tags()));
        result.put("prerequisites", ids(node.prerequisites()));
        result.putInt("ticks", node.researchTicks());
        result.putDouble("energy", node.aePerTick());
        ListTag inputs = new ListTag();
        node.inputs().forEach(input -> inputs.add(input(input, registries)));
        result.put("inputs", inputs);
        return result;
    }

    public static ResearchNode readNode(CompoundTag tag, HolderLookup.Provider registries) {
        ObjectList<ResearchInput> inputs = new ObjectArrayList<>();
        for (Tag value : tag.getList("inputs", Tag.TAG_COMPOUND)) inputs.add(readInput((CompoundTag) value, registries));
        return new ResearchNode(ResourceLocation.parse(tag.getString("id")), ResearchScope.valueOf(tag.getString("scope")),
                tag.getString("name"), tag.getString("description"), ResourceLocation.parse(tag.getString("icon")),
                new ResearchTarget(readIds(tag, "target_items"), readIds(tag, "target_tags")), inputs,
                readIds(tag, "prerequisites"), tag.getInt("ticks"), tag.getDouble("energy"));
    }
}
