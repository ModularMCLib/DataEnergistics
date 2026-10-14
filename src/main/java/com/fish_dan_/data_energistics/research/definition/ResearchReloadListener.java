package com.fish_dan_.data_energistics.research.definition;

import com.fish_dan_.data_energistics.Data_Energistics;
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

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
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
        ObjectList<ResearchInput> inputs = json.has("inputs")
                ? ResearchInputParser.parse(json.get("inputs"), operations) : original.inputs();
        return new ResearchNode(original.id(), original.scope(), original.name(),
                json.has("description") ? string(json.get("description"), "description") : original.description(),
                original.icon(), target, inputs,
                json.has("prerequisites") ? ids(json, "prerequisites") : original.prerequisites(),
                json.has("research_ticks") ? number(json.get("research_ticks"), "research_ticks").intValueExact() : original.researchTicks(),
                json.has("ae_per_tick") ? number(json.get("ae_per_tick"), "ae_per_tick").doubleValue() : original.aePerTick());
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
        if (json.has(field)) for (JsonElement element : json.getAsJsonArray(field)) result.add(ResourceLocation.parse(string(element, field)));
        return result;
    }
}
