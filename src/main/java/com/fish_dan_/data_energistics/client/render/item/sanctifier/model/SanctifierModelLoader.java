package com.fish_dan_.data_energistics.client.render.item.sanctifier.model;

import net.minecraft.util.GsonHelper;
import net.neoforged.neoforge.client.model.geometry.IGeometryLoader;
import net.neoforged.neoforge.client.model.obj.ObjLoader;
import net.neoforged.neoforge.client.model.obj.ObjModel;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import java.util.EnumMap;

/**
 * Loads the two independently textured swords through NeoForge's reloadable OBJ/MTL loader.
 */
public final class SanctifierModelLoader implements IGeometryLoader<SanctifierGeometry> {

    @Override
    public SanctifierGeometry read(JsonObject json, JsonDeserializationContext context) {
        JsonObject parts = GsonHelper.getAsJsonObject(json, "parts");
        if (parts.size() != SanctifierGeometry.Part.values().length) {
            throw new JsonParseException("Sanctifier requires the primary and secondary sword OBJ parts");
        }
        var models = new EnumMap<SanctifierGeometry.Part, ObjModel>(SanctifierGeometry.Part.class);
        for (var part : SanctifierGeometry.Part.values()) {
            JsonObject settings = new JsonObject();
            settings.addProperty("model", GsonHelper.getAsString(parts, part.fileName()));
            settings.addProperty("automatic_culling", false);
            settings.addProperty("shade_quads", true);
            settings.addProperty("flip_v", true);
            settings.addProperty("emissive_ambient", false);
            models.put(part, ObjLoader.INSTANCE.read(settings, context));
        }
        return new SanctifierGeometry(models);
    }
}
