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
 * Loads the six native OBJ assets through NeoForge's normal, reloadable OBJ/MTL loader.
 */
public final class SanctifierModelLoader implements IGeometryLoader<SanctifierGeometry> {

    @Override
    public SanctifierGeometry read(JsonObject json, JsonDeserializationContext context) {
        JsonObject parts = GsonHelper.getAsJsonObject(json, "parts");
        if (parts.size() != SanctifierGeometry.Part.values().length) {
            throw new JsonParseException("Sanctifier requires exactly six articulated OBJ parts");
        }
        var models = new EnumMap<SanctifierGeometry.Part, ObjModel>(SanctifierGeometry.Part.class);
        for (var part : SanctifierGeometry.Part.values()) {
            JsonObject settings = new JsonObject();
            settings.addProperty("model", GsonHelper.getAsString(parts, part.fileName()));
            settings.addProperty("automatic_culling", false);
            settings.addProperty("shade_quads", !part.emissive());
            settings.addProperty("flip_v", true);
            settings.addProperty("emissive_ambient", false);
            models.put(part, ObjLoader.INSTANCE.read(settings, context));
        }
        float staticDraw = GsonHelper.getAsFloat(json, "static_draw", 0);
        if (!Float.isFinite(staticDraw) || staticDraw < 0 || staticDraw > 1) {
            throw new JsonParseException("Sanctifier static_draw must be a finite fraction in [0, 1]");
        }
        return new SanctifierGeometry(models, staticDraw);
    }
}
