package com.fish_dan_.data_energistics.client.render.item.sanctifier;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;

import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Rigid grip anchors shared by the visible hands, blade and scabbard. Coordinates are blocks. */
final class SanctifierRig {

    // The usable hilt is 11.35 model units: a four-pixel hand covers about 46% at this scale.
    static final float SCALE = 0.76F;
    private static final Vector3f SWORD_GRIP = new Vector3f(0, -2, 0).div(16);
    private static final Vector3f FIST_GRIP = new Vector3f(0, 0.6F, 0).div(16);
    private static final Vector3f RELEASE_PIVOT = new Vector3f(0, 8, 0).div(16);
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

    static float opening(float draw) {
        return ramp(draw, 0.25F, 0.54F);
    }

    /** Short latch clearance followed by a lever motion through the open rear spine. */
    static Matrix4f unhinged(Matrix4f scabbard, float draw) {
        return new Matrix4f(scabbard).translate(-3.2F * ramp(draw, 0.50F, 0.62F) / 16,
                -1.2F * ramp(draw, 0.18F, 0.27F) / 16, 0)
                .translate(RELEASE_PIVOT).rotateZ(radians(40 * opening(draw)))
                .translate(-RELEASE_PIVOT.x, -RELEASE_PIVOT.y, -RELEASE_PIVOT.z);
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
        return scabbardAtHandle(new Matrix4f(local.last().pose()).translate(0.42F, 0.70F, 0.08F)
                .rotateX(radians(67)), -90);
    }

    /** Fix the handle at the anchor; -Y follows the rear slot, while -X opens out of it. */
    private static Matrix4f scabbardAtHandle(Matrix4f anchor, float yaw) {
        return anchor.rotateY(radians(yaw)).scale(SCALE)
                .translate(-CARRY_GRIP.x, -CARRY_GRIP.y, -CARRY_GRIP.z);
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

    static Matrix4f carried(HumanoidModel<?> model, HumanoidArm mainArm) {
        Vector3f position = palm(model, mainArm.getOpposite()).getTranslation(new Vector3f());
        float sign = mainArm == HumanoidArm.RIGHT ? 1 : -1;
        return scabbardAtHandle(new Matrix4f().translation(position).rotateZ(radians(-sign * 65)),
                -90 + 15 * sign);
    }

    static Frame thirdPerson(HumanoidModel<?> model, LivingEntity entity, float draw) {
        Matrix4f holster = holster(model);
        if (!entity.getOffhandItem().isEmpty()) {
            Matrix4f sword = unhinged(holster, draw);
            return new Frame(blend(sword, swordInHand(model, entity.getMainArm()), ready(draw)), holster);
        }
        Matrix4f lifted = blendScabbard(holster, carried(model, entity.getMainArm()), lift(draw));
        Matrix4f sword = unhinged(lifted, draw);
        return new Frame(blend(sword, swordInHand(model, entity.getMainArm()), ready(draw)),
                blendScabbard(lifted, holster, holstered(draw)));
    }

    private static Matrix4f swordInHand(HumanoidModel<?> model, HumanoidArm mainArm) {
        float sign = mainArm == HumanoidArm.RIGHT ? 1 : -1;
        return palm(model, mainArm).rotateX(radians(-90))
                .rotateY(radians(sign * 24)).scale(SCALE).translate(-SWORD_GRIP.x, -SWORD_GRIP.y, -SWORD_GRIP.z);
    }

    static FirstPersonFrame firstPerson(HumanoidArm mainArm, SanctifierAnimationStates.Input input) {
        float draw = input.draw();
        var combat = SanctifierAnimation.sample(draw, 0, input.charge(), input.age());
        float sign = mainArm == HumanoidArm.RIGHT ? 1 : -1;
        float lift = lift(draw), ready = ready(draw);
        Matrix4f lifted = scabbardAtHandle(new Matrix4f()
                .translation(-sign * 0.38F, -1.1F + 0.63F * lift, -0.78F)
                .rotateZ(radians(sign * (160 - 45 * lift))), -90 + 15 * sign * lift);
        Matrix4f sword = unhinged(lifted, draw);
        float root = (float) Math.sqrt(input.swing());
        float stroke = (float) Math.sin(root * Math.PI);
        float twist = (float) Math.sin(input.swing() * input.swing() * Math.PI);
        // These are ItemInHandRenderer's sword swing transforms, sampled from the vanilla clock.
        Matrix4f held = new Matrix4f().translation(sign * (0.40F - 0.4F * stroke * ready),
                -0.38F + 0.2F * (float) Math.sin(root * Math.PI * 2) * ready + combat.combatY() * 0.018F,
                -0.78F - 0.2F * (float) Math.sin(input.swing() * Math.PI) * ready)
                .rotateY(radians(sign * (45 - 20 * twist * ready)))
                .rotateZ(radians(-sign * 20 * stroke * ready)).rotateX(radians(-80 * stroke * ready))
                .rotateY(radians(-sign * 45))
                .rotateZ(radians(-sign * 20 + combat.swordRoll())).rotateX(radians(-12 + combat.swordPitch()))
                .rotateY(radians(-sign * 18 + combat.swordYaw())).scale(SCALE)
                .translate(-FIST_GRIP.x, -FIST_GRIP.y, -FIST_GRIP.z);
        Matrix4f sheath = scabbardAtHandle(new Matrix4f()
                .translation(-sign * (0.38F + 0.1F * holstered(draw)),
                        -1.1F + 0.63F * lift - 0.9F * holstered(draw), -0.78F)
                .rotateZ(radians(sign * (160 - 45 * lift + 45 * holstered(draw)))),
                -90 + 15 * sign * lift * (1 - holstered(draw)));
        Frame assembly = new Frame(blend(sword, held, ready), sheath);
        return new FirstPersonFrame(assembly,
                firstPersonHand(assembly.sword(), new Vector3f(FIST_GRIP), sign),
                firstPersonHand(sheath, new Vector3f(CARRY_GRIP), -sign));
    }

    /** Aim the forearm back toward its shoulder, then roll the wrist to grasp the hilt. */
    private static Matrix4f firstPersonHand(Matrix4f part, Vector3f localGrip, float side) {
        Vector3f grip = part.transformPosition(localGrip);
        Vector3f forearm = new Vector3f(grip).sub(side * 0.54F, -0.82F, 0.08F).normalize();
        Vector3f hilt = part.transformDirection(new Vector3f(0, 1, 0)).normalize();
        hilt.fma(-hilt.dot(forearm), forearm).normalize();
        Vector3f across = new Vector3f(forearm).cross(hilt).normalize();
        Quaternionf wrist = new Matrix3f().setColumn(0, across).setColumn(1, forearm).setColumn(2, hilt)
                .getNormalizedRotation(new Quaternionf());
        return new Matrix4f().translationRotateScale(grip, wrist, new Vector3f(1));
    }

    /** Quaternion interpolation keeps the rigid scabbard intact throughout the lift. */
    static Matrix4f blend(Matrix4f from, Matrix4f to, float fraction) {
        Vector3f position = from.getTranslation(new Vector3f()).lerp(to.getTranslation(new Vector3f()), fraction);
        Vector3f scale = from.getScale(new Vector3f()).lerp(to.getScale(new Vector3f()), fraction);
        Quaternionf rotation = from.getUnnormalizedRotation(new Quaternionf())
                .slerp(to.getUnnormalizedRotation(new Quaternionf()), fraction);
        return new Matrix4f().translationRotateScale(position, rotation, scale);
    }

    /** Keep the handle on its interpolated attachment point throughout lifting and returning. */
    private static Matrix4f blendScabbard(Matrix4f from, Matrix4f to, float fraction) {
        return blend(new Matrix4f(from).translate(CARRY_GRIP), new Matrix4f(to).translate(CARRY_GRIP), fraction)
                .translate(-CARRY_GRIP.x, -CARRY_GRIP.y, -CARRY_GRIP.z);
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

    record FirstPersonFrame(Frame assembly, Matrix4f mainHand, Matrix4f supportHand) {}
}
