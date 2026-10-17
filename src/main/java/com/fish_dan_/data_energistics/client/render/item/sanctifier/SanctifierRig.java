package com.fish_dan_.data_energistics.client.render.item.sanctifier;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;

import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Rigid grip anchors shared by the visible hands, blade and scabbard. Coordinates are blocks. */
final class SanctifierRig {

    // The usable hilt is 11.35 model units: a four-pixel hand covers about 46% at this scale.
    static final float SCALE = 0.76F;
    static final float EXTRACTION_DISTANCE = 45;
    private static final Vector3f SWORD_GRIP = new Vector3f(0, -2, 0).div(16);
    private static final Vector3f CARRY_GRIP = new Vector3f(3.85F, 17.3F, 0).div(16);

    private SanctifierRig() {}

    static float lift(float draw) {
        return ramp(draw, 0.06F, 0.23F);
    }

    static float support(float draw) {
        return ramp(draw, 0, 0.12F) * (1 - ramp(draw, 0.94F, 1));
    }

    static float holstered(float draw) {
        return ramp(draw, 0.79F, 0.94F);
    }

    static float extraction(float draw) {
        return ramp(draw, 0.25F, 0.62F);
    }

    static float ready(float draw) {
        return ramp(draw, 0.64F, 0.79F);
    }

    static float latch(float draw) {
        return ramp(draw, 0.18F, 0.28F) * 38;
    }

    static float ignition(float draw) {
        return ramp(draw, 0.57F, 0.92F);
    }

    static Matrix4f holster(HumanoidModel<?> model) {
        PoseStack local = new PoseStack();
        model.body.translateAndRotate(local);
        return new Matrix4f(local.last().pose()).translate(0.42F, 0.38F, 0.08F)
                .rotateX(radians(67)).rotateZ(radians(-12)).scale(SCALE);
    }

    static Matrix4f palm(HumanoidModel<?> model, HumanoidArm arm) {
        PoseStack local = new PoseStack();
        model.translateToHand(arm, local);
        float sign = arm == HumanoidArm.RIGHT ? 1 : -1;
        // Match the vanilla item grip, including PlayerModel's slim-arm correction.
        return new Matrix4f(local.last().pose()).translate(-sign / 16, 10.0F / 16, -2.0F / 16);
    }

    static Vector3f shoulder(HumanoidModel<?> model, HumanoidArm arm) {
        PoseStack local = new PoseStack();
        model.translateToHand(arm, local);
        return local.last().pose().getTranslation(new Vector3f());
    }

    static Matrix4f carried(HumanoidModel<?> model, HumanoidArm mainArm, float draw) {
        Vector3f position = palm(model, mainArm.getOpposite()).getTranslation(new Vector3f());
        float sign = mainArm == HumanoidArm.RIGHT ? 1 : -1;
        return new Matrix4f().translation(position).rotateY(radians(-12 * sign))
                .rotateZ(radians(sign * (-55 + 30 * ready(draw)))).scale(SCALE)
                .translate(-CARRY_GRIP.x, -CARRY_GRIP.y, -CARRY_GRIP.z);
    }

    static Frame thirdPerson(HumanoidModel<?> model, LivingEntity entity, float draw) {
        Matrix4f holster = holster(model);
        if (!entity.getOffhandItem().isEmpty()) {
            Matrix4f sword = new Matrix4f(holster).translate(0, -EXTRACTION_DISTANCE * extraction(draw) / 16, 0);
            return new Frame(blend(sword, swordInHand(model, entity.getMainArm()), ready(draw)), holster);
        }
        Matrix4f lifted = blend(holster, carried(model, entity.getMainArm(), draw), lift(draw));
        Matrix4f sword = new Matrix4f(lifted).translate(0, -EXTRACTION_DISTANCE * extraction(draw) / 16, 0);
        return new Frame(blend(sword, swordInHand(model, entity.getMainArm()), ready(draw)),
                blend(lifted, holster, holstered(draw)));
    }

    private static Matrix4f swordInHand(HumanoidModel<?> model, HumanoidArm mainArm) {
        float sign = mainArm == HumanoidArm.RIGHT ? 1 : -1;
        return palm(model, mainArm).rotateX(radians(-90))
                .rotateY(radians(sign * 24)).scale(SCALE).translate(-SWORD_GRIP.x, -SWORD_GRIP.y, -SWORD_GRIP.z);
    }

    static Frame firstPerson(HumanoidArm mainArm, float draw, SanctifierAnimation.Pose combat) {
        float sign = mainArm == HumanoidArm.RIGHT ? 1 : -1;
        float lift = lift(draw), ready = ready(draw);
        float depth = -1.85F - 1.1F * extraction(draw) * (1 - ready);
        Matrix4f lifted = new Matrix4f().translation(-sign * 0.5F, -1.6F + 1.22F * lift, depth)
                .rotateY(radians(-12 * sign)).rotateZ(radians(sign * (170 - 70 * lift + 50 * ready)))
                .scale(SCALE).translate(-CARRY_GRIP.x, -CARRY_GRIP.y, -CARRY_GRIP.z);
        Matrix4f sword = new Matrix4f(lifted).translate(0, -EXTRACTION_DISTANCE * extraction(draw) / 16, 0);
        Matrix4f held = new Matrix4f().translation(sign * 0.56F, -0.54F + combat.combatY() * 0.018F, -1.5F)
                .rotateZ(radians(-sign * 18 + combat.swordRoll())).rotateX(radians(-12 + combat.swordPitch()))
                .rotateY(radians(-sign * 18 + combat.swordYaw())).scale(SCALE)
                .translate(-SWORD_GRIP.x, -SWORD_GRIP.y, -SWORD_GRIP.z);
        Matrix4f sheath = new Matrix4f(lifted);
        sheath.m31(sheath.m31() - 1.2F * holstered(draw));
        return new Frame(blend(sword, held, ready), sheath);
    }

    /** Quaternion interpolation keeps the rigid scabbard intact throughout the lift. */
    private static Matrix4f blend(Matrix4f from, Matrix4f to, float fraction) {
        Vector3f position = from.getTranslation(new Vector3f()).lerp(to.getTranslation(new Vector3f()), fraction);
        Vector3f scale = from.getScale(new Vector3f()).lerp(to.getScale(new Vector3f()), fraction);
        Quaternionf rotation = from.getUnnormalizedRotation(new Quaternionf())
                .slerp(to.getUnnormalizedRotation(new Quaternionf()), fraction);
        return new Matrix4f().translationRotateScale(position, rotation, scale);
    }

    static Vector3f carryGrip(Matrix4f matrix) {
        return matrix.transformPosition(new Vector3f(CARRY_GRIP));
    }

    static Vector3f swordGrip(Matrix4f matrix) {
        return matrix.transformPosition(new Vector3f(SWORD_GRIP));
    }

    private static float ramp(float value, float start, float end) {
        float t = Math.clamp((value - start) / (end - start), 0, 1);
        return t * t * (3 - 2 * t);
    }

    private static float radians(float value) {
        return (float) Math.toRadians(value);
    }

    record Frame(Matrix4f sword, Matrix4f scabbard) {}
}
