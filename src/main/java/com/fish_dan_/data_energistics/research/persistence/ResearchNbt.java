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
        for (Tag value : readList(tag, field, Tag.TAG_STRING)) result.add(ResourceLocation.parse(value.getAsString()));
        return result;
    }

    public static ListTag prerequisiteGroups(ObjectList<ObjectSet<ResourceLocation>> groups) {
        ListTag result = new ListTag();
        for (ObjectSet<ResourceLocation> group : groups) result.add(ids(group));
        return result;
    }

    public static ObjectList<ObjectSet<ResourceLocation>> readPrerequisiteGroups(CompoundTag tag, String field) {
        ObjectList<ObjectSet<ResourceLocation>> result = new ObjectArrayList<>();
        for (Tag value : readList(tag, field, Tag.TAG_LIST)) {
            ListTag group = (ListTag) value;
            ObjectSet<ResourceLocation> ids = new ObjectOpenHashSet<>();
            for (Tag id : group) ids.add(ResourceLocation.parse(id.getAsString()));
            if (ids.isEmpty()) throw new IllegalArgumentException("Research field " + field + " contains an empty prerequisite group");
            result.add(ids);
        }
        return result;
    }

    /** Missing optional lists are empty; present lists must retain their declared element type. */
    public static ListTag readList(CompoundTag tag, String field, int elementType) {
        Tag value = tag.get(field);
        if (value == null) return new ListTag();
        if (value instanceof ListTag list && (list.isEmpty() || list.getElementType() == elementType)) return list;
        throw new IllegalArgumentException("Research field " + field + " must be an NBT list of type " + elementType);
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
        result.put("required_prerequisites", ids(node.requiredPrerequisites()));
        result.put("optional_prerequisites", prerequisiteGroups(node.optionalPrerequisiteGroups()));
        result.putInt("ticks", node.researchTicks());
        result.putDouble("energy", node.aePerTick());
        ListTag inputs = new ListTag();
        node.inputs().forEach(input -> inputs.add(input(input, registries)));
        result.put("inputs", inputs);
        return result;
    }

    public static ResearchNode readNode(CompoundTag tag, HolderLookup.Provider registries) {
        ObjectList<ResearchInput> inputs = new ObjectArrayList<>();
        for (Tag value : readList(tag, "inputs", Tag.TAG_COMPOUND)) inputs.add(readInput((CompoundTag) value, registries));
        return new ResearchNode(ResourceLocation.parse(tag.getString("id")), ResearchScope.valueOf(tag.getString("scope")),
                tag.getString("name"), tag.getString("description"), ResourceLocation.parse(tag.getString("icon")),
                new ResearchTarget(readIds(tag, "target_items"), readIds(tag, "target_tags")), inputs,
                readIds(tag, "required_prerequisites"), readPrerequisiteGroups(tag, "optional_prerequisites"),
                tag.getInt("ticks"), tag.getDouble("energy"));
    }
}
