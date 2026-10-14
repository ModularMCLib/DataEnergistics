package com.fish_dan_.data_energistics.research.definition;

import com.fish_dan_.data_energistics.api.research.ResearchInput;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;

import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;

/** Author-facing resource maps; the group and map key supply the resource identity exactly once. */
final class ResearchInputParser {

    private ResearchInputParser() {}

    static ObjectList<ResearchInput> parse(JsonElement value, RegistryOps<JsonElement> operations) {
        if (!(value instanceof JsonObject groups)) {
            throw new IllegalArgumentException("inputs must be an object with items, fluids or resources maps; material arrays are unsupported");
        }
        ObjectList<ResearchInput> result = new ObjectArrayList<>();
        for (var group : groups.entrySet()) {
            String name = group.getKey();
            String field = "inputs." + name;
            if (!name.equals("items") && !name.equals("fluids") && !name.equals("resources")) {
                throw new IllegalArgumentException(field + ": expected items, fluids or resources");
            }
            if (!(group.getValue() instanceof JsonObject materials)) {
                throw new IllegalArgumentException(field + " must be an ID-to-amount object");
            }
            for (var material : materials.entrySet()) {
                String resourceField = field + "[" + material.getKey() + "]";
                try {
                    ResourceLocation id = ResourceLocation.parse(material.getKey());
                    JsonElement entry = material.getValue();
                    if (entry instanceof JsonArray variants) {
                        if (variants.isEmpty()) throw new IllegalArgumentException("variant array must not be empty");
                        for (int index = 0; index < variants.size(); index++) {
                            add(result, name, id, variants.get(index), operations, resourceField + "[" + index + "]");
                        }
                    } else {
                        add(result, name, id, entry, operations, resourceField);
                    }
                } catch (RuntimeException error) {
                    throw new IllegalArgumentException(resourceField + ": " + error.getMessage(), error);
                }
            }
        }
        return result;
    }

    private static void add(ObjectList<ResearchInput> result, String group, ResourceLocation id, JsonElement value,
                            RegistryOps<JsonElement> operations, String field) {
        JsonObject data;
        long amount;
        if (value instanceof JsonObject object) {
            data = object.deepCopy();
            amount = data.has("amount") ? amount(data.remove("amount"), field + ".amount") : 1;
        } else {
            data = new JsonObject();
            amount = amount(value, field);
        }
        AEKey key;
        if (group.equals("resources")) {
            if (data.has("#t") || data.has("key_type")) {
                throw new IllegalArgumentException("AE key type is supplied by the map key; omit #t and key_type");
            }
            AEKeyType type = AEKeyType.CODEC.parse(operations, new JsonPrimitive(id.toString())).getOrThrow();
            if (type == AEKeyType.items() || type == AEKeyType.fluids()) {
                throw new IllegalArgumentException("item and fluid keys belong in the items or fluids group");
            }
            // Decode the actual type codec directly: the generic codec can hide errors behind a missing-content key.
            key = type.codec().codec().parse(operations, data).getOrThrow();
        } else {
            for (String name : data.keySet()) {
                if (!name.equals("components")) throw new IllegalArgumentException("unexpected field " + name + "; expected amount or components");
            }
            data.addProperty("id", id.toString());
            key = group.equals("items") ? AEItemKey.CODEC.parse(operations, data).getOrThrow() : AEFluidKey.CODEC.parse(operations, data).getOrThrow();
        }
        result.add(new ResearchInput(key, amount));
    }

    private static long amount(JsonElement value, String field) {
        if (value instanceof JsonPrimitive primitive && primitive.isNumber()) {
            try {
                return primitive.getAsBigDecimal().longValueExact();
            } catch (ArithmeticException error) {
                throw new IllegalArgumentException(field + " must be an integer within the long range", error);
            }
        }
        throw new IllegalArgumentException(field + " must be an integer within the long range");
    }
}
