package com.fish_dan_.data_energistics.research.definition;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.research.AdvancementResearchCondition;
import com.fish_dan_.data_energistics.api.research.FtbQuestTaskCondition;
import com.fish_dan_.data_energistics.api.research.ResearchCondition;
import com.fish_dan_.data_energistics.api.research.ResearchInput;
import com.fish_dan_.data_energistics.api.research.ResearchNode;
import com.fish_dan_.data_energistics.api.research.ResearchTarget;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.jspecify.annotations.Nullable;

import java.io.Reader;
import java.math.BigDecimal;

/** Reloads data/<namespace>/researchtechnology/<id>.json without reading other mods' research formats. */
public final class ResearchReloadListener extends SimplePreparableReloadListener<Object2ObjectMap<ResourceLocation, ResearchNode>> {

    private static final String DIRECTORY = "researchtechnology";
    private final RegistryOps<JsonElement> operations;

    public ResearchReloadListener(HolderLookup.Provider registries) {
        this.operations = registries.createSerializationContext(JsonOps.INSTANCE);
    }

    @Override
    protected Object2ObjectMap<ResourceLocation, ResearchNode> prepare(ResourceManager resources, ProfilerFiller profiler) {
        var nodes = ResearchDefinitions.defaults();
        resources.listResources(DIRECTORY, path -> path.getPath().endsWith(".json")).forEach((path, resource) -> {
            if (!path.getNamespace().equals(Data_Energistics.MODID)) return;
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(path.getNamespace(),
                    path.getPath().substring(DIRECTORY.length() + 1, path.getPath().length() - 5));
            try (Reader reader = resource.openAsReader()) {
                if (!nodes.containsKey(id)) throw new IllegalArgumentException("ID was not registered by Java: " + id);
                ResearchNode original = nodes.get(id);
                nodes.put(id, override(original, JsonParser.parseReader(reader).getAsJsonObject()));
            } catch (Exception error) {
                nodes.remove(id);
                Data_Energistics.LOGGER.error("Rejected research file {}: {}", path, error.getMessage(), error);
            }
        });
        return nodes;
    }

    @Override
    protected void apply(Object2ObjectMap<ResourceLocation, ResearchNode> nodes, ResourceManager resources, ProfilerFiller profiler) {
        ResearchDefinitions.publish(nodes);
    }

    private ResearchNode override(ResearchNode original, JsonObject json) {
        if (json.has("id") && !ResourceLocation.parse(string(json.get("id"), "id")).equals(original.id())) {
            throw new IllegalArgumentException("id must match the file path");
        }
        ResearchTarget target = original.target();
        if (json.has("target")) {
            JsonObject object = json.getAsJsonObject("target");
            target = new ResearchTarget(ids(object, "items"), ids(object, "tags"));
            for (ResourceLocation item : target.items()) {
                if (!BuiltInRegistries.ITEM.containsKey(item)) throw new IllegalArgumentException("target.items: unknown item " + item);
            }
        }
        ObjectList<ResearchInput> inputs = json.has("inputs") ? ResearchInputParser.parse(json.get("inputs"), operations) : original.inputs();
        Prerequisites prerequisites = json.has("prerequisites") ? prerequisites(json.get("prerequisites")) :
                new Prerequisites(original.requiredPrerequisites(), original.optionalPrerequisiteGroups());
        Conditions conditions = json.has("conditions") ? conditions(json.get("conditions")) :
                new Conditions(original.requiredConditions(), original.optionalConditionGroups());
        ObjectSet<ResourceLocation> mutuallyExclusive = json.has("mutually_exclusive") ?
                ids(json, "mutually_exclusive") : original.mutuallyExclusive();
        return new ResearchNode(original.id(), original.scope(), original.name(),
                json.has("description") ? string(json.get("description"), "description") : original.description(),
                original.icon(), target, inputs,
                prerequisites.required(), prerequisites.optionalGroups(),
                conditions.required(), conditions.optionalGroups(), mutuallyExclusive,
                json.has("research_ticks") ? number(json.get("research_ticks"), "research_ticks").intValueExact() : original.researchTicks(),
                json.has("ae_per_tick") ? number(json.get("ae_per_tick"), "ae_per_tick").doubleValue() : original.aePerTick());
    }

    private static Conditions conditions(@Nullable JsonElement value) {
        if (!(value instanceof JsonObject object)) throw new IllegalArgumentException("conditions must be an object");
        ObjectSet<ResearchCondition> required = conditionArray(object.get("required"), "conditions.required");
        ObjectList<ObjectSet<ResearchCondition>> optionalGroups = new ObjectArrayList<>();
        if (!object.has("optional")) return new Conditions(required, optionalGroups);
        JsonElement optionalValue = object.get("optional");
        if (!(optionalValue instanceof JsonArray groups)) {
            throw new IllegalArgumentException("conditions.optional must be an array of condition arrays");
        }
        for (int index = 0; index < groups.size(); index++) {
            JsonElement groupValue = groups.get(index);
            if (!(groupValue instanceof JsonArray group)) {
                throw new IllegalArgumentException("conditions.optional[" + index + "] must be an array");
            }
            ObjectSet<ResearchCondition> parsed = conditionArray(group, "conditions.optional[" + index + "]");
            if (parsed.isEmpty()) throw new IllegalArgumentException("conditions.optional[" + index + "] must not be empty");
            optionalGroups.add(parsed);
        }
        return new Conditions(required, optionalGroups);
    }

    private static ObjectSet<ResearchCondition> conditionArray(@Nullable JsonElement value, String field) {
        ObjectSet<ResearchCondition> result = new ObjectOpenHashSet<>();
        if (value == null) return result;
        if (!(value instanceof JsonArray array)) throw new IllegalArgumentException(field + " must be an array");
        for (int index = 0; index < array.size(); index++) {
            ResearchCondition condition = condition(array.get(index), field + "[" + index + "]");
            if (!result.add(condition)) throw new IllegalArgumentException(field + " contains a duplicate condition");
        }
        return result;
    }

    private static ResearchCondition condition(@Nullable JsonElement value, String field) {
        if (!(value instanceof JsonObject object)) throw new IllegalArgumentException(field + " must be an object");
        String type = string(object.get("type"), field + ".type");
        return switch (type) {
            case "advancement" -> new AdvancementResearchCondition(ResourceLocation.parse(string(object.get("id"), field + ".id")));
            case "ftbquests_task" -> new FtbQuestTaskCondition(number(object.get("task"), field + ".task").longValueExact(),
                    object.has("quest") ? ResourceLocation.parse(string(object.get("quest"), field + ".quest")) : null);
            default -> throw new IllegalArgumentException(field + ".type has unknown condition type " + type);
        };
    }

    private static Prerequisites prerequisites(@Nullable JsonElement value) {
        if (!(value instanceof JsonObject object)) throw new IllegalArgumentException("prerequisites must be an object");
        ObjectSet<ResourceLocation> required = ids(object, "required");
        ObjectList<ObjectSet<ResourceLocation>> optionalGroups = new ObjectArrayList<>();
        if (!object.has("optional")) return new Prerequisites(required, optionalGroups);
        JsonElement optionalValue = object.get("optional");
        if (!(optionalValue instanceof JsonArray groups)) {
            throw new IllegalArgumentException("prerequisites.optional must be an array of prerequisite ID arrays");
        }
        for (int index = 0; index < groups.size(); index++) {
            JsonElement groupValue = groups.get(index);
            if (!(groupValue instanceof JsonArray group)) {
                throw new IllegalArgumentException("prerequisites.optional[" + index + "] must be an array");
            }
            ObjectSet<ResourceLocation> ids = new ObjectOpenHashSet<>();
            for (int item = 0; item < group.size(); item++) {
                ResourceLocation id = ResourceLocation.parse(string(group.get(item), "prerequisites.optional[" + index + "][" + item + "]"));
                if (!ids.add(id)) throw new IllegalArgumentException("prerequisites.optional[" + index + "] contains duplicate ID " + id);
            }
            if (ids.isEmpty()) throw new IllegalArgumentException("prerequisites.optional[" + index + "] must not be empty");
            optionalGroups.add(ids);
        }
        return new Prerequisites(required, optionalGroups);
    }

    private static String string(@Nullable JsonElement value, String field) {
        if (value instanceof JsonPrimitive primitive && primitive.isString()) return primitive.getAsString();
        throw new IllegalArgumentException(field + " must be a JSON string");
    }

    private static BigDecimal number(@Nullable JsonElement value, String field) {
        if (value instanceof JsonPrimitive primitive && primitive.isNumber()) return primitive.getAsBigDecimal();
        throw new IllegalArgumentException(field + " must be a JSON number");
    }

    private static ObjectSet<ResourceLocation> ids(JsonObject json, String field) {
        ObjectSet<ResourceLocation> result = new ObjectOpenHashSet<>();
        if (!json.has(field)) return result;
        JsonElement value = json.get(field);
        if (!(value instanceof JsonArray array)) throw new IllegalArgumentException(field + " must be an array");
        for (int index = 0; index < array.size(); index++) {
            ResourceLocation id = ResourceLocation.parse(string(array.get(index), field + "[" + index + "]"));
            if (!result.add(id)) throw new IllegalArgumentException(field + " contains duplicate ID " + id);
        }
        return result;
    }

    private record Conditions(ObjectSet<ResearchCondition> required, ObjectList<ObjectSet<ResearchCondition>> optionalGroups) {}

    private record Prerequisites(ObjectSet<ResourceLocation> required, ObjectList<ObjectSet<ResourceLocation>> optionalGroups) {}
}
