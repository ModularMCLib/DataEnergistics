package com.fish_dan_.data_energistics.client.render.item.sanctifier;

import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.neoforged.neoforge.client.model.geometry.IGeometryBakingContext;
import net.neoforged.neoforge.client.model.geometry.IUnbakedGeometry;
import net.neoforged.neoforge.client.model.obj.ObjModel;

import java.util.EnumMap;
import java.util.function.Function;

/**
 * Retains independently baked OBJ parts so model reloads also replace their materials and UVs.
 */
public final class SanctifierGeometry implements IUnbakedGeometry<SanctifierGeometry> {

    private final EnumMap<Part, ObjModel> parts;
    private final float staticDraw;

    SanctifierGeometry(EnumMap<Part, ObjModel> parts, float staticDraw) {
        this.parts = new EnumMap<>(parts);
        this.staticDraw = staticDraw;
    }

    @Override
    public BakedModel bake(IGeometryBakingContext context, ModelBaker baker,
                           Function<Material, TextureAtlasSprite> sprites, ModelState state, ItemOverrides overrides) {
        var baked = new EnumMap<Part, BakedModel>(Part.class);
        this.parts.forEach((part, model) -> baked.put(part,
                model.bake(context, baker, sprites, state, ItemOverrides.EMPTY)));
        return new SanctifierBakedModel(baked, overrides, this.staticDraw);
    }

    enum Part {

        SWORD("sword", false),
        BLADE_EDGE("blade_edge", true),
        SCABBARD("scabbard", false),
        SCABBARD_LATCH("scabbard_latch", false),
        SWORD_LIGHTS("sword_lights", true),
        SCABBARD_LIGHTS("scabbard_lights", true);

        private final String fileName;
        private final boolean emissive;

        Part(String fileName, boolean emissive) {
            this.fileName = fileName;
            this.emissive = emissive;
        }

        String fileName() {
            return this.fileName;
        }

        boolean emissive() {
            return this.emissive;
        }

        boolean scabbard() {
            return this == SCABBARD || this == SCABBARD_LATCH || this == SCABBARD_LIGHTS;
        }
    }
}
