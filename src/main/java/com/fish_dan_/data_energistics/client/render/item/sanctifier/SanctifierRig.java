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

    static final float SCALE = 0.34F;
    static final float EXTRACTION_DISTANCE = 45;
    private static final float FIRST_PERSON_SCALE = 0.31F;
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
        return new Matrix4f(local.last().pose()).translate(0.37F, 0.42F, 0.12F)
                .rotateX(radians(25)).rotateZ(radians(-12)).scale(SCALE);
    }

    static Matrix4f palm(HumanoidModel<?> model, HumanoidArm arm) {
        PoseStack local = new PoseStack();
        model.translateToHand(arm, local);
        return new Matrix4f(local.last().pose()).translate(0, 0.625F, 0);
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
        return palm(model, mainArm).rotateX((float) Math.PI).rotateZ(radians(sign * 12)).rotateY(radians(sign * 40)).scale(SCALE);
    }

    static Frame firstPerson(HumanoidArm mainArm, float draw, SanctifierAnimation.Pose combat) {
        float sign = mainArm == HumanoidArm.RIGHT ? 1 : -1;
        float lift = lift(draw), ready = ready(draw);
        Matrix4f lifted = new Matrix4f().translation(-sign * 0.42F, -1.3F + 0.95F * lift, -1.5F)
                .rotateY(radians(-12 * sign)).rotateZ(radians(sign * (170 - 70 * lift + 50 * ready)))
                .scale(FIRST_PERSON_SCALE).translate(-CARRY_GRIP.x, -CARRY_GRIP.y, -CARRY_GRIP.z);
        Matrix4f sword = new Matrix4f(lifted).translate(0, -EXTRACTION_DISTANCE * extraction(draw) / 16, 0);
        Matrix4f held = new Matrix4f().translation(sign * 0.38F, -0.52F + combat.combatY() * 0.018F, -1.05F)
                .rotateY(radians(sign * 50)).rotateZ(radians(sign * (-14 + combat.swordRoll())))
                .rotateX(radians(-10 + combat.swordPitch())).rotateY(radians(combat.swordYaw())).scale(FIRST_PERSON_SCALE);
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

    private static float ramp(float value, float start, float end) {
        float t = Math.clamp((value - start) / (end - start), 0, 1);
        return t * t * (3 - 2 * t);
    }

    private static float radians(float value) {
        return (float) Math.toRadians(value);
    }

    record Frame(Matrix4f sword, Matrix4f scabbard) {}
}
