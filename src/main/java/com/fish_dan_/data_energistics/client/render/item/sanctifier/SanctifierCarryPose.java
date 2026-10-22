package com.fish_dan_.data_energistics.client.render.item.sanctifier;

import com.fish_dan_.data_energistics.client.render.item.blade.BladeGripPose;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Grip-based diagonal back mount and an overhead draw arc, in the player's model coordinate system. */
public final class SanctifierCarryPose {

    private SanctifierCarryPose() {}

    public static Matrix4f mounted(Matrix4f body) {
        // Body +Y points down. Keep the blade's +Y diagonal and its +Z cutting edge downward, not into the back.
        return new Matrix4f(body).translate(-0.22F, 0.12F, 0.20F)
                .rotateZ((float) Math.toRadians(-48)).rotateX((float) Math.toRadians(-7)).rotateY((float) Math.toRadians(-90))
                .scale(0.72F);
    }

    public static Matrix4f draw(Matrix4f mount, Matrix4f hand, Matrix4f body, float progress) {
        float bounded = Math.clamp(progress, 0, 1);
        Matrix4f lifted = new Matrix4f(mount);
        Vector3f lift = body.transformDirection(new Vector3f(-0.12F, -0.48F, -0.06F));
        lifted.m30(lifted.m30() + lift.x).m31(lifted.m31() + lift.y).m32(lifted.m32() + lift.z);
        if (bounded <= 0.45F) return blend(mount, lifted, smooth(bounded / 0.45F));
        return blend(lifted, hand, smooth((bounded - 0.45F) / 0.55F));
    }

    public static Matrix4f firstPerson(float progress, Matrix4f held) {
        float bounded = Math.clamp(progress, 0, 1);
        Matrix4f aboveShoulder = new Matrix4f().translation(0.70F, 0.75F, -0.55F)
                .rotateXYZ((float) Math.toRadians(25), (float) Math.PI, (float) Math.toRadians(-35)).scale(held.getScale(new Vector3f()).x);
        Matrix4f transform = blend(aboveShoulder, held, smooth(Math.clamp((bounded - 0.35F) / 0.65F, 0, 1)));
        if (bounded < 0.35F) transform.translate(0, (0.35F - bounded) * 5, 0);
        return transform;
    }

    private static Matrix4f blend(Matrix4f start, Matrix4f end, float progress) {
        Vector3f grip = new Vector3f(0, 0, BladeGripPose.SANCTIFIER_PRIMARY_GRIP_Z);
        Vector3f position = start.transformPosition(new Vector3f(grip)).lerp(end.transformPosition(new Vector3f(grip)), progress);
        Quaternionf rotation = start.getUnnormalizedRotation(new Quaternionf()).slerp(end.getUnnormalizedRotation(new Quaternionf()), progress);
        float scale = start.getScale(new Vector3f()).x * (1 - progress) + end.getScale(new Vector3f()).x * progress;
        return new Matrix4f().translation(position).rotate(rotation).scale(scale).translate(0, 0, -BladeGripPose.SANCTIFIER_PRIMARY_GRIP_Z);
    }

    private static float smooth(float value) {
        return value * value * (3 - 2 * value);
    }
}
