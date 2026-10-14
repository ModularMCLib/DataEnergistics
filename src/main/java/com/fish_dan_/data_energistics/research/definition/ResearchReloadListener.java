package com.fish_dan_.data_energistics.research.definition;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.research.ResearchInput;
import com.fish_dan_.data_energistics.api.research.ResearchNode;
import com.fish_dan_.data_energistics.api.research.ResearchTarget;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;

import java.io.Reader;

/** Reloads data/<namespace>/research/<id>.json, retaining Java defaults for absent fields. */
public final class ResearchReloadListener extends SimplePreparableReloadListener<Object2ObjectMap<ResourceLocation, ResearchNode>> {

    @Override
    protected Object2ObjectMap<ResourceLocation, ResearchNode> prepare(ResourceManager resources, ProfilerFiller profiler) {
        var nodes = ResearchDefinitions.defaults();
        resources.listResources("research", path -> path.getPath().endsWith(".json")).forEach((path, resource) -> {
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(path.getNamespace(),
                    path.getPath().substring("research/".length(), path.getPath().length() - 5));
            try (Reader reader = resource.openAsReader()) {
                ResearchNode original = nodes.get(id);
                if (original == null) throw new IllegalArgumentException("ID was not registered by Java: " + id);
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

    private static ResearchNode override(ResearchNode original, JsonObject json) {
        if (json.has("id") && !ResourceLocation.parse(json.get("id").getAsString()).equals(original.id())) {
            throw new IllegalArgumentException("id must match the file path");
        }
        ResearchTarget target = original.target();
        if (json.has("target")) {
            JsonObject object = json.getAsJsonObject("target");
            target = new ResearchTarget(ids(object, "items"), ids(object, "tags"));
            for (ResourceLocation item : target.items()) {
                if (!BuiltInRegistries.ITEM.containsKey(item)) throw new IllegalArgumentException("Unknown target item: " + item);
            }
        }
        ObjectList<ResearchInput> inputs = original.inputs();
        if (json.has("inputs")) {
            inputs = new ObjectArrayList<>();
            for (JsonElement element : json.getAsJsonArray("inputs")) {
                JsonObject input = element.getAsJsonObject();
                AEKey key = switch (input.get("type").getAsString()) {
                    case "item" -> {
                        ResourceLocation item = ResourceLocation.parse(input.get("id").getAsString());
                        if (!BuiltInRegistries.ITEM.containsKey(item)) throw new IllegalArgumentException("Unknown input item: " + item);
                        yield AEItemKey.of(BuiltInRegistries.ITEM.get(item));
                    }
                    case "fluid" -> {
                        ResourceLocation fluid = ResourceLocation.parse(input.get("id").getAsString());
                        if (!BuiltInRegistries.FLUID.containsKey(fluid)) throw new IllegalArgumentException("Unknown input fluid: " + fluid);
                        yield AEFluidKey.of(BuiltInRegistries.FLUID.get(fluid));
                    }
                    case "ae_key" -> AEKey.CODEC.parse(JsonOps.INSTANCE, input.get("key")).getOrThrow();
                    default -> throw new IllegalArgumentException("inputs.type must be item, fluid or ae_key");
                };
                inputs.add(new ResearchInput(key, input.get("amount").getAsLong()));
            }
        }
        return new ResearchNode(original.id(), original.scope(), original.name(),
                json.has("description") ? json.get("description").getAsString() : original.description(),
                original.icon(), target, inputs,
                json.has("prerequisites") ? ids(json, "prerequisites") : original.prerequisites(),
                json.has("research_ticks") ? json.get("research_ticks").getAsInt() : original.researchTicks(),
                json.has("ae_per_tick") ? json.get("ae_per_tick").getAsDouble() : original.aePerTick());
    }

    private static ObjectSet<ResourceLocation> ids(JsonObject json, String field) {
        ObjectSet<ResourceLocation> result = new ObjectOpenHashSet<>();
        if (json.has(field)) for (JsonElement element : json.getAsJsonArray(field)) result.add(ResourceLocation.parse(element.getAsString()));
        return result;
    }
}
