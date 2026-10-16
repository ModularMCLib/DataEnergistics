package com.fish_dan_.data_energistics.research.persistence;

import com.fish_dan_.data_energistics.api.research.AdvancementResearchCondition;
import com.fish_dan_.data_energistics.api.research.FtbQuestTaskCondition;
import com.fish_dan_.data_energistics.api.research.ResearchCondition;
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

import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;

import java.util.UUID;

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

    public static ListTag exclusions(Object2ObjectMap<ResourceLocation, ResourceLocation> winners) {
        ListTag result = new ListTag();
        winners.forEach((blocked, winner) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString("blocked", blocked.toString());
            entry.putString("winner", winner.toString());
            result.add(entry);
        });
        return result;
    }

    public static Object2ObjectMap<ResourceLocation, ResourceLocation> readExclusions(CompoundTag tag, String field) {
        Object2ObjectMap<ResourceLocation, ResourceLocation> result = new Object2ObjectOpenHashMap<>();
        for (Tag value : readList(tag, field, Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) value;
            ResourceLocation blocked = ResourceLocation.parse(entry.getString("blocked"));
            ResourceLocation winner = ResourceLocation.parse(entry.getString("winner"));
            if (result.putIfAbsent(blocked, winner) != null) {
                throw new IllegalArgumentException("Research field " + field + " contains duplicate exclusions for " + blocked);
            }
        }
        return result;
    }

    public static ListTag uuidIds(Object2ObjectMap<UUID, ObjectSet<ResourceLocation>> values) {
        ListTag result = new ListTag();
        values.forEach((uuid, nodeIds) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("uuid", uuid);
            entry.put("nodes", ids(nodeIds));
            result.add(entry);
        });
        return result;
    }

    public static ListTag uuids(Iterable<UUID> values) {
        ListTag result = new ListTag();
        for (UUID uuid : values) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("uuid", uuid);
            result.add(entry);
        }
        return result;
    }

    public static ListTag uuidExclusions(Object2ObjectMap<UUID, Object2ObjectMap<ResourceLocation, ResourceLocation>> values) {
        ListTag result = new ListTag();
        values.forEach((uuid, locks) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("uuid", uuid);
            entry.put("locks", exclusions(locks));
            result.add(entry);
        });
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

    public static ListTag conditions(Iterable<ResearchCondition> conditions) {
        ListTag result = new ListTag();
        for (ResearchCondition condition : conditions) {
            CompoundTag value = new CompoundTag();
            if (condition instanceof AdvancementResearchCondition advancement) {
                value.putString("type", "advancement");
                value.putString("id", advancement.id().toString());
            } else if (condition instanceof FtbQuestTaskCondition task) {
                value.putString("type", "ftbquests_task");
                value.putLong("task", task.taskId());
                if (task.quest() != null) value.putString("quest", task.quest().toString());
            } else {
                throw new IllegalArgumentException("Unknown research condition type " + condition.getClass().getSimpleName());
            }
            result.add(value);
        }
        return result;
    }

    public static ObjectSet<ResearchCondition> readConditions(CompoundTag tag, String field) {
        ObjectSet<ResearchCondition> result = new ObjectOpenHashSet<>();
        for (Tag value : readList(tag, field, Tag.TAG_COMPOUND)) {
            CompoundTag condition = (CompoundTag) value;
            ResearchCondition parsed = switch (condition.getString("type")) {
                case "advancement" -> new AdvancementResearchCondition(ResourceLocation.parse(condition.getString("id")));
                case "ftbquests_task" -> new FtbQuestTaskCondition(condition.getLong("task"),
                        condition.contains("quest") ? ResourceLocation.parse(condition.getString("quest")) : null);
                default -> throw new IllegalArgumentException("Unknown research condition type in " + field);
            };
            if (!result.add(parsed)) throw new IllegalArgumentException("Duplicate research condition in " + field);
        }
        return result;
    }

    public static ListTag conditionGroups(ObjectList<ObjectSet<ResearchCondition>> groups) {
        ListTag result = new ListTag();
        for (ObjectSet<ResearchCondition> group : groups) result.add(conditions(group));
        return result;
    }

    public static ObjectList<ObjectSet<ResearchCondition>> readConditionGroups(CompoundTag tag, String field) {
        ObjectList<ObjectSet<ResearchCondition>> result = new ObjectArrayList<>();
        for (Tag value : readList(tag, field, Tag.TAG_LIST)) {
            ListTag group = (ListTag) value;
            CompoundTag wrapper = new CompoundTag();
            wrapper.put("conditions", group);
            ObjectSet<ResearchCondition> conditions = readConditions(wrapper, "conditions");
            if (conditions.isEmpty()) throw new IllegalArgumentException("Research field " + field + " contains an empty condition group");
            result.add(conditions);
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
        result.put("required_conditions", conditions(node.requiredConditions()));
        result.put("optional_conditions", conditionGroups(node.optionalConditionGroups()));
        result.put("mutually_exclusive", ids(node.mutuallyExclusive()));
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
                readConditions(tag, "required_conditions"), readConditionGroups(tag, "optional_conditions"),
                readIds(tag, "mutually_exclusive"),
                tag.getInt("ticks"), tag.getDouble("energy"));
    }
}
