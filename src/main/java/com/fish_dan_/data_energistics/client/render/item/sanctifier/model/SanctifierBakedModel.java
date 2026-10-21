package com.fish_dan_.data_energistics.client.render.item.sanctifier.model;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.client.render.item.sanctifier.model.SanctifierGeometry.Part;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

import java.util.EnumMap;

/**
 * Per-render models keep entity/hand poses separate, including both hands and inventory previews.
 */
public final class SanctifierBakedModel extends BakedModelWrapper<BakedModel> {

    private final EnumMap<Part, ObjectList<BakedQuad>> parts;
    private final ItemOverrides overrides;

    SanctifierBakedModel(EnumMap<Part, BakedModel> models, ItemOverrides resourceOverrides) {
        super(models.get(Part.SWORD));
        this.parts = new EnumMap<>(Part.class);
        models.forEach((part, model) -> this.parts.put(part,
                new ObjectImmutableList<>(model.getQuads(null, null, RandomSource.create(0), ModelData.EMPTY, null))));
        this.overrides = new ItemOverrides() {

            @Override
            public BakedModel resolve(BakedModel model, ItemStack stack, @Nullable ClientLevel level,
                                      @Nullable LivingEntity entity, int seed) {
                BakedModel replacement = resourceOverrides.resolve(model, stack, level, entity, seed);
                if (replacement != null && replacement != model) return replacement;
                return new HeldSanctifier();
            }
        };
    }

    /** Resolves each render so reloads cannot leave stale sprites in the execution renderer. */
    public static @Nullable SanctifierBakedModel current() {
        BakedModel model = Minecraft.getInstance().getModelManager()
                .getModel(ModelResourceLocation.inventory(Data_Energistics.id("data_sanctifier")));
        return model instanceof SanctifierBakedModel sanctifier ? sanctifier : null;
    }

    /** Draws a render-thread frame with this resource generation's quads; pose-stack changes are restored. */
    public void renderPrimary(ItemStack stack, PoseStack poses, MultiBufferSource buffers, int light,
                              Matrix4f transform) {
        VertexConsumer vertices = ItemRenderer.getFoilBufferDirect(buffers, Sheets.cutoutBlockSheet(), false, stack.hasFoil());
        poses.pushPose();
        poses.mulPose(transform);
        for (BakedQuad quad : this.parts.get(Part.SWORD)) {
            vertices.putBulkData(poses.last(), quad, 1, 1, 1, 1, light, OverlayTexture.NO_OVERLAY);
        }
        poses.popPose();
    }

    /** Draws the rigid secondary blade with the selected saber color at the supplied world transform. */
    public void renderSecondary(ItemStack stack, PoseStack poses, MultiBufferSource buffers, int light,
                                Matrix4f transform, int color, boolean powered) {
        VertexConsumer vertices = ItemRenderer.getFoilBufferDirect(buffers, Sheets.cutoutBlockSheet(), false, stack.hasFoil());
        poses.pushPose();
        poses.mulPose(transform);
        float red = ((color >>> 16) & 0xFF) / 255.0F;
        float green = ((color >>> 8) & 0xFF) / 255.0F;
        float blue = (color & 0xFF) / 255.0F;
        for (BakedQuad quad : this.parts.get(Part.SECONDARY_SWORD)) {
            vertices.putBulkData(poses.last(), quad, 1, 1, 1, 1, light, OverlayTexture.NO_OVERLAY);
        }
        for (BakedQuad quad : this.parts.get(Part.SECONDARY_BLADE)) {
            vertices.putBulkData(poses.last(), quad, red, green, blue, 1,
                    powered ? LightTexture.FULL_BRIGHT : light, OverlayTexture.NO_OVERLAY);
        }
        poses.popPose();
    }

    /** Uses the same grip and hand transform as the primary blade without centering the grip-based OBJ twice. */
    public void renderHeldSecondary(ItemStack stack, ItemDisplayContext context, boolean leftHand,
                                    PoseStack poses, MultiBufferSource buffers, int light, int color, boolean powered) {
        poses.pushPose();
        poses.mulPose(secondaryHandTransform(context, leftHand));
        renderSecondary(stack, poses, buffers, light, new Matrix4f(), color, powered);
        poses.popPose();
    }

    public Matrix4f secondaryHandTransform(ItemDisplayContext context, boolean leftHand) {
        PoseStack poses = new PoseStack();
        this.originalModel.applyTransform(context, poses, leftHand);
        // Both blades use the primary grip pivot; the secondary grip is 0.0644 model units farther forward.
        return new Matrix4f(poses.last().pose()).translate(0, 0, -0.0644F);
    }

    /** Applies the resource-defined hand pose to the grip-based primary OBJ. */
    public void renderHeldPrimary(ItemStack stack, ItemDisplayContext context, PoseStack poses,
                                  MultiBufferSource buffers, int light) {
        poses.pushPose();
        this.originalModel.applyTransform(context, poses, false);
        renderPrimary(stack, poses, buffers, light, new Matrix4f());
        poses.popPose();
    }

    @Override
    public ItemOverrides getOverrides() {
        return this.overrides;
    }

    @Override
    public ObjectList<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random) {
        return side == null ? this.parts.get(Part.SWORD) : ObjectLists.emptyList();
    }

    @Override
    public ObjectList<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random,
                                          ModelData data, @Nullable RenderType renderType) {
        return getQuads(state, side, random);
    }

    private final class HeldSanctifier extends BakedModelWrapper<BakedModel> {

        private HeldSanctifier() {
            super(SanctifierBakedModel.this.originalModel);
        }

        @Override
        public BakedModel applyTransform(ItemDisplayContext context, PoseStack poseStack, boolean leftHand) {
            this.originalModel.applyTransform(context, poseStack, leftHand);
            // OBJ coordinates are centered on the grip; ItemRenderer subtracts 0.5 after this call.
            poseStack.translate(0.5, 0.5, 0.5);
            return this;
        }

        @Override
        public ObjectList<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random) {
            return side == null ? SanctifierBakedModel.this.parts.get(Part.SWORD) : ObjectLists.emptyList();
        }

        @Override
        public ObjectList<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random,
                                              ModelData data, @Nullable RenderType renderType) {
            return getQuads(state, side, random);
        }

        @Override
        public ObjectList<BakedModel> getRenderPasses(ItemStack stack, boolean fabulous) {
            return ObjectLists.singleton(this);
        }
    }
}
