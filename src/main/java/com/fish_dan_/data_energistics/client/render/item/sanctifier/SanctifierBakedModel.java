package com.fish_dan_.data_energistics.client.render.item.sanctifier;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.client.render.item.sanctifier.SanctifierGeometry.Part;

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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.QuadTransformers;
import net.neoforged.neoforge.client.model.data.ModelData;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Transformation;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.EnumMap;

/**
 * Per-render models keep entity/hand poses separate, including both hands and inventory previews.
 */
final class SanctifierBakedModel extends BakedModelWrapper<BakedModel> {

    private final EnumMap<Part, ObjectList<BakedQuad>> parts;
    private final ItemOverrides overrides;
    private final ObjectList<BakedQuad> staticQuads;
    private final ObjectList<BakedQuad> poweredQuads;
    private final ObjectList<BakedQuad> thrownQuads;
    private final ObjectList<BakedQuad> poweredThrownQuads;
    private final float staticDraw;

    SanctifierBakedModel(EnumMap<Part, BakedModel> models, ItemOverrides resourceOverrides, float staticDraw) {
        super(models.get(Part.SWORD));
        this.staticDraw = staticDraw;
        this.parts = new EnumMap<>(Part.class);
        models.forEach((part, model) -> this.parts.put(part,
                new ObjectImmutableList<>(model.getQuads(null, null, RandomSource.create(0), ModelData.EMPTY, null))));
        this.staticQuads = geometry(staticDraw, SanctifierAnimation.sample(staticDraw, 0, 0, 0), false, true);
        this.poweredQuads = geometry(staticDraw, SanctifierAnimation.sample(staticDraw, 0, 0, 0), true, true);
        this.thrownQuads = geometry(1, SanctifierAnimation.sample(1, 0, 0, 0), false, false);
        this.poweredThrownQuads = geometry(1, SanctifierAnimation.sample(1, 0, 0, 0), true, false);
        this.overrides = new ItemOverrides() {

            @Override
            public BakedModel resolve(BakedModel model, ItemStack stack, @Nullable ClientLevel level,
                                      @Nullable LivingEntity entity, int seed) {
                BakedModel replacement = resourceOverrides.resolve(model, stack, level, entity, seed);
                if (replacement != null && replacement != model) return replacement;
                return new HeldSanctifier(stack, entity);
            }
        };
    }

    /** Resolve on each render so a resource reload cannot leave stale sprites in the carry layer. */
    static @Nullable SanctifierBakedModel current() {
        BakedModel model = Minecraft.getInstance().getModelManager()
                .getModel(ModelResourceLocation.inventory(Data_Energistics.id("data_sanctifier")));
        return model instanceof SanctifierBakedModel sanctifier ? sanctifier : null;
    }

    void renderAssembly(ItemStack stack, PoseStack poses, MultiBufferSource buffers, int light,
                        SanctifierRig.Frame frame, float draw, boolean powered, boolean includeScabbard) {
        VertexConsumer vertices = ItemRenderer.getFoilBufferDirect(buffers, Sheets.cutoutBlockSheet(), false, stack.hasFoil());
        float ignition = SanctifierRig.ignition(draw);
        Matrix4f edge = new Matrix4f(frame.sword()).translate(0, 8.0F / 16, 0)
                .scale(1, Math.max(0.001F, ignition), 1).translate(0, -8.0F / 16, 0);
        Matrix4f latch = new Matrix4f(frame.scabbard()).translate(-1.7F / 16, 6.0F / 16, 0)
                .rotateZ(radians(SanctifierRig.latch(draw))).translate(1.7F / 16, -6.0F / 16, 0);
        for (Part part : Part.values()) {
            if (!includeScabbard && part.scabbard()) continue;
            if (part == Part.BLADE_EDGE && ignition == 0) continue;
            Matrix4f transform = switch (part) {
                case SWORD, SWORD_LIGHTS -> frame.sword();
                case BLADE_EDGE -> edge;
                case SCABBARD, SCABBARD_LIGHTS -> frame.scabbard();
                case SCABBARD_LATCH -> latch;
            };
            poses.pushPose();
            poses.mulPose(transform);
            float brightness = part.emissive() && !powered ? 0.19F : 1;
            int partLight = part.emissive() && powered ? LightTexture.FULL_BRIGHT : light;
            for (BakedQuad quad : this.parts.get(part)) {
                vertices.putBulkData(poses.last(), quad, brightness, brightness, brightness, 1,
                        partLight, OverlayTexture.NO_OVERLAY);
            }
            poses.popPose();
        }
    }

    @Override
    public ItemOverrides getOverrides() {
        return this.overrides;
    }

    @Override
    public ObjectList<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random) {
        return side == null ? this.staticQuads : ObjectLists.emptyList();
    }

    @Override
    public ObjectList<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random,
                                          ModelData data, @Nullable RenderType renderType) {
        return getQuads(state, side, random);
    }

    private final class HeldSanctifier extends BakedModelWrapper<BakedModel> {

        private final ItemStack stack;
        private final @Nullable LivingEntity entity;
        private ObjectList<BakedQuad> quads = staticQuads;

        private HeldSanctifier(ItemStack stack, @Nullable LivingEntity entity) {
            super(SanctifierBakedModel.this.originalModel);
            this.stack = stack;
            this.entity = entity;
        }

        @Override
        public BakedModel applyTransform(ItemDisplayContext context, PoseStack poseStack, boolean leftHand) {
            this.originalModel.applyTransform(context, poseStack, leftHand);
            // OBJ coordinates are centered on the grip; ItemRenderer subtracts 0.5 after this call.
            poseStack.translate(0.5, 0.5, 0.5);
            boolean handView = context.firstPerson() || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND || context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
            boolean projectile = context == ItemDisplayContext.NONE;
            if (!handView && !projectile) poseStack.translate(0, -17.5 / 16.0, 0);
            boolean powered = SanctifierAnimationStates.isPowered(this.stack);
            if (!handView) {
                this.quads = projectile ? (powered ? poweredThrownQuads : thrownQuads) : (powered ? poweredQuads : staticQuads);
                return this;
            }
            float draw = staticDraw;
            SanctifierAnimation.Pose pose = SanctifierAnimation.sample(draw, 0, 0, 0);
            if (this.entity != null) {
                HumanoidArm arm = context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND ? HumanoidArm.LEFT : HumanoidArm.RIGHT;
                InteractionHand hand = this.entity.getMainArm() == arm ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
                if (this.entity instanceof Player && hand == InteractionHand.MAIN_HAND) {
                    // The player layer / first-person event draws both grip-anchored parts together.
                    this.quads = ObjectLists.emptyList();
                    return this;
                }
                if (this.entity.getItemInHand(hand).is(this.stack.getItem())) {
                    float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
                    var input = SanctifierAnimationStates.input(this.entity, hand, partial);
                    draw = input.draw();
                    // Vanilla has already applied the hand's attack transform outside this model.
                    pose = SanctifierAnimation.sample(draw, 0, input.charge(), input.age());
                }
            }
            this.quads = geometry(draw, pose, powered, true);
            return this;
        }

        @Override
        public ObjectList<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random) {
            return side == null ? this.quads : ObjectLists.emptyList();
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

    private ObjectList<BakedQuad> geometry(float draw, SanctifierAnimation.Pose pose, boolean powered, boolean includeScabbard) {
        Matrix4f sword = SanctifierRig.blend(SanctifierRig.unhinged(new Matrix4f(), draw), new Matrix4f(), SanctifierRig.ready(draw))
                .translate(0, pose.combatY() / 16, 0)
                .rotateXYZ(radians(pose.swordPitch()), radians(pose.swordYaw()), radians(pose.swordRoll()));
        Matrix4f sheath = new Matrix4f().translate(pose.scabbardX() / 16, pose.scabbardY() / 16, pose.scabbardZ() / 16)
                .translate(0, 6.0F / 16, 0).rotateZ(radians(pose.scabbardRoll())).translate(0, -6.0F / 16, 0);
        Matrix4f latch = new Matrix4f(sheath).translate(-1.7F / 16, 6.0F / 16, 0)
                .rotateZ(radians(pose.latchRoll())).translate(1.7F / 16, -6.0F / 16, 0);
        Matrix4f edge = new Matrix4f(sword).translate(0, 8.0F / 16, 0)
                .scale(1, Math.max(0.001F, pose.ignition()), 1).translate(0, -8.0F / 16, 0);
        var result = new ObjectArrayList<BakedQuad>(4500);
        for (Part part : Part.values()) {
            if (!includeScabbard && part.scabbard()) continue;
            if (part == Part.BLADE_EDGE && pose.ignition() == 0) continue;
            Matrix4f matrix = switch (part) {
                case SWORD, SWORD_LIGHTS -> sword;
                case BLADE_EDGE -> edge;
                case SCABBARD, SCABBARD_LIGHTS -> sheath;
                case SCABBARD_LATCH -> latch;
            };
            append(result, this.parts.get(part), matrix, part.emissive(), powered);
        }
        return new ObjectImmutableList<>(result);
    }

    private static void append(ObjectList<BakedQuad> target, ObjectList<BakedQuad> source, Matrix4f matrix,
                               boolean emissive, boolean powered) {
        Transformation transformation = new Transformation(matrix);
        var transformer = QuadTransformers.applying(transformation);
        for (BakedQuad quad : source) {
            Vector3f normal = new Vector3f(quad.getDirection().step());
            transformation.transformNormal(normal);
            BakedQuad copy = new BakedQuad(quad.getVertices().clone(), quad.getTintIndex(),
                    Direction.getNearest(normal.x, normal.y, normal.z), quad.getSprite(),
                    !emissive && quad.isShade(), !emissive && quad.hasAmbientOcclusion());
            transformer.processInPlace(copy);
            if (emissive) {
                if (powered) QuadTransformers.settingMaxEmissivity().processInPlace(copy);
                else QuadTransformers.applyingColor(0xFF303030).processInPlace(copy);
            }
            target.add(copy);
        }
    }

    private static float radians(float degrees) {
        return (float) Math.toRadians(degrees);
    }
}
