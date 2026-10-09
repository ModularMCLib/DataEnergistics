package com.fish_dan_.data_energistics.client.render.item.crossbow;

import com.fish_dan_.data_energistics.item.powered.MatterConvergingCrossbowMode;

import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockElement;
import net.minecraft.client.renderer.block.model.FaceBakery;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.ClientHooks;
import net.neoforged.neoforge.client.model.IModelBuilder;
import net.neoforged.neoforge.client.model.QuadTransformers;
import net.neoforged.neoforge.client.model.geometry.IGeometryBakingContext;
import net.neoforged.neoforge.client.model.geometry.IUnbakedGeometry;

import com.mojang.math.Transformation;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.function.Function;

public final class CrossbowGeometry implements IUnbakedGeometry<CrossbowGeometry> {

    private final ObjectList<ObjectList<Element>> poses;
    private final ObjectList<Element> specialAmmo;

    CrossbowGeometry(ObjectList<ObjectList<Element>> poses, ObjectList<Element> specialAmmo) {
        this.poses = poses;
        this.specialAmmo = specialAmmo;
    }

    @Override
    public BakedModel bake(IGeometryBakingContext context, ModelBaker baker,
                           Function<Material, TextureAtlasSprite> spriteGetter, ModelState modelState,
                           ItemOverrides overrides) {
        FaceBakery bakery = new FaceBakery();
        ObjectList<ObjectList<Part>> frames = this.poses.stream()
                .map(pose -> bakeParts(pose, context, spriteGetter, bakery))
                .collect(ObjectArrayList.toList());
        ObjectList<Part> ammo = bakeParts(this.specialAmmo, context, spriteGetter, bakery);
        Matrix4f root = modelState.getRotation().applyOrigin(new Vector3f(0.5F))
                .compose(context.getRootTransform()).getMatrix();
        ResourceLocation renderType = context.getRenderTypeHint();
        var builder = IModelBuilder.of(false, context.useBlockLight(), true, context.getTransforms(),
                ItemOverrides.EMPTY, spriteGetter.apply(context.getMaterial("particle")),
                context.getRenderType(renderType != null ? renderType : ResourceLocation.withDefaultNamespace("translucent")));
        render(frames, ammo, CrossbowAnimation.Pose.stationary(false), false, root).forEach(builder::addUnculledFace);
        return new CrossbowBakedModel(builder.build(), frames, ammo, root, overrides);
    }

    private static ObjectList<Part> bakeParts(ObjectList<Element> elements, IGeometryBakingContext context,
                                              Function<Material, TextureAtlasSprite> sprites, FaceBakery bakery) {
        ObjectArrayList<Part> result = new ObjectArrayList<>(elements.size());
        for (Element element : elements) {
            ObjectArrayList<BakedQuad> faces = new ObjectArrayList<>();
            element.cube.faces.forEach((direction, face) -> faces.add(bakery.bakeQuad(
                    new Vector3f(-8.0F), new Vector3f(8.0F), face,
                    sprites.apply(context.getMaterial(face.texture())), direction,
                    BlockModelRotation.X0_Y0, null, element.cube.shade)));
            result.add(new Part(new ObjectImmutableList<>(faces), element.pose, element.motion, element.deployment));
        }
        return new ObjectImmutableList<>(result);
    }

    static ObjectList<BakedQuad> render(ObjectList<ObjectList<Part>> frames, ObjectList<Part> specialAmmo,
                                        CrossbowAnimation.Pose pose, boolean special, Matrix4f root) {
        ObjectList<Part> folded = frames.getFirst();
        ObjectList<Part> active = frames.get(pose.stage() + 1);
        ObjectArrayList<BakedQuad> quads = new ObjectArrayList<>(320);
        CrossbowRig rig = new CrossbowRig(pose);
        for (int i = 0; i < active.size(); i++) {
            Part from = folded.get(i);
            Part to = active.get(i);
            if (pose.mode() == MatterConvergingCrossbowMode.GRENADE) {
                // Use the charge-stage UV/light data on the folded geometry, without extending it.
                append(quads, to.deployment == CrossbowDeployment.RAIL ? to.quads : from.quads,
                        new Matrix4f(root).mul(from.pose.matrix()));
                continue;
            }
            if (pose.mode() == MatterConvergingCrossbowMode.RAIL && to.deployment == CrossbowDeployment.BOW) {
                append(quads, from.quads, new Matrix4f(root).mul(from.pose.matrix()));
                continue;
            }
            Matrix4f transform = new Matrix4f(root).mul(rig.transform(from.pose, to.pose, to.deployment, to.motion));
            append(quads, pose.deployment() == 0.0F ? from.quads : to.quads, transform);
        }
        if (special && pose.tipUnfold() == 1.0F) {
            for (Part part : specialAmmo) {
                Matrix4f transform = new Matrix4f(root).translate(part.pose.center()).rotate(part.pose.rotation()).scale(part.pose.size());
                int start = quads.size();
                append(quads, part.quads, transform);
                for (int i = start; i < quads.size(); i++) {
                    QuadTransformers.settingMaxEmissivity().processInPlace(quads.get(i));
                    QuadTransformers.applyingColor(0xFFCC88FF).processInPlace(quads.get(i));
                }
            }
        }
        return new ObjectImmutableList<>(quads);
    }

    public record Anchors(Vector3f muzzle, Vector3f left, Vector3f right, Vector3f back) {}

    /** The authored upper/lower rail housings and front tips, transformed by the same rig as their vertices. */
    static Anchors anchors(ObjectList<ObjectList<Part>> frames, CrossbowAnimation.Pose pose) {
        CrossbowRig rig = new CrossbowRig(pose);
        Matrix4f upper = anchorTransform(frames, rig, 11);
        Matrix4f lower = anchorTransform(frames, rig, 17);
        Vector3f left = upper.transformPosition(new Vector3f(-0.5F, 0, 0.35F))
                .add(lower.transformPosition(new Vector3f(-0.5F, 0, 0.35F))).mul(0.5F);
        Vector3f right = upper.transformPosition(new Vector3f(0.5F, 0, 0.35F))
                .add(lower.transformPosition(new Vector3f(0.5F, 0, 0.35F))).mul(0.5F);
        Vector3f back = upper.transformPosition(new Vector3f(0, 0, 0.5F))
                .sub(upper.transformPosition(new Vector3f(0, 0, 0))).normalize();
        Vector3f muzzle = anchorTransform(frames, rig, 26).transformPosition(new Vector3f(0, 0, -0.5F))
                .add(anchorTransform(frames, rig, 27).transformPosition(new Vector3f(0, 0, -0.5F))).mul(0.5F);
        return new Anchors(muzzle, left, right, back);
    }

    private static Matrix4f anchorTransform(ObjectList<ObjectList<Part>> frames, CrossbowRig rig, int index) {
        Part from = frames.getFirst().get(index), to = frames.get(1).get(index);
        return rig.transform(from.pose, to.pose, to.deployment, to.motion);
    }

    private static void append(ObjectList<BakedQuad> output, ObjectList<BakedQuad> source, Matrix4f matrix) {
        Transformation transformation = new Transformation(matrix);
        var transformer = QuadTransformers.applying(transformation);
        for (BakedQuad quad : source) {
            Vector3f normal = new Vector3f(quad.getDirection().step());
            transformation.transformNormal(normal);
            if (matrix.determinant3x3() < 0.0F) {
                normal.negate();
            }
            BakedQuad transformed = new BakedQuad(quad.getVertices().clone(), quad.getTintIndex(),
                    Direction.getNearest(normal.x, normal.y, normal.z), quad.getSprite(), quad.isShade(),
                    quad.hasAmbientOcclusion());
            transformer.processInPlace(transformed);
            // Keep the authored inverted hull winding and derive normals from the final vertices.
            ClientHooks.fillNormal(transformed.getVertices(), transformed.getDirection());
            output.add(transformed);
        }
    }

    record Element(BlockElement cube, CrossbowPartPose pose, CrossbowMotion motion, CrossbowDeployment deployment) {}

    record Part(ObjectList<BakedQuad> quads, CrossbowPartPose pose, CrossbowMotion motion, CrossbowDeployment deployment) {}
}
