package com.fish_dan_.data_energistics.client.render.item.sanctifier.model;

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

    SanctifierGeometry(EnumMap<Part, ObjModel> parts) {
        this.parts = new EnumMap<>(parts);
    }

    @Override
    public BakedModel bake(IGeometryBakingContext context, ModelBaker baker,
                           Function<Material, TextureAtlasSprite> sprites, ModelState state, ItemOverrides overrides) {
        var baked = new EnumMap<Part, BakedModel>(Part.class);
        this.parts.forEach((part, model) -> baked.put(part,
                model.bake(context, baker, sprites, state, ItemOverrides.EMPTY)));
        return new SanctifierBakedModel(baked, overrides);
    }

    enum Part {

        SWORD("sword"),
        SECONDARY_SWORD("secondary_sword"),
        SECONDARY_BLADE("secondary_blade");

        private final String fileName;

        Part(String fileName) {
            this.fileName = fileName;
        }

        String fileName() {
            return this.fileName;
        }
    }
}
