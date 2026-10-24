package com.fish_dan_.data_energistics.client.render.item.blade;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Actual OBJ grip centers and a skin-arm frame whose palm remains on the blade throughout an animation. */
public final class BladeGripPose {

    public static final float SANCTIFIER_PRIMARY_GRIP_Z = -0.085F;
    public static final float SANCTIFIER_SECONDARY_GRIP_Z = -0.02057875F;
    // Light saber OBJ coordinates are centered by ItemRenderer before its display transform is applied.
    public static final float LIGHT_SABER_GRIP_X = 0.000060625F;
    public static final float LIGHT_SABER_GRIP_Y = -0.23F;
    public static final float LIGHT_SABER_GRIP_Z = -0.02057875F;

    private BladeGripPose() {}

    /** Uses camera coordinates; the arm keeps player scale while the blade may have a separate uniform scale. */
    public static Matrix4f firstPersonArm(Matrix4f grip, boolean left, boolean slim) {
        Vector3f palm = grip.getTranslation(new Vector3f());
        Vector3f forearm = new Vector3f(palm).sub(left ? -0.72F : 0.72F, -0.86F, -0.18F).normalize();
        Vector3f across = grip.transformDirection(new Vector3f(0, 1, 0)).normalize();
        across.fma(-across.dot(forearm), forearm);
        // A blade aimed along the arm has no unique wrist roll; choose the camera's horizontal direction there.
        if (across.lengthSquared() < 0.0001F) across.set(1, 0, 0).fma(-forearm.x, forearm);
        across.normalize();
        if (left) across.negate();
        Vector3f normal = new Vector3f(across).cross(forearm);
        Quaternionf rotation = new Matrix4f().m00(across.x).m01(across.y).m02(across.z)
                .m10(forearm.x).m11(forearm.y).m12(forearm.z)
                .m20(normal.x).m21(normal.y).m22(normal.z).getNormalizedRotation(new Quaternionf());
        // Vanilla skin cubes end at Y=10/16; the center of the last two pixels is the closed palm.
        float palmX = (left ? 1 : -1) * (slim ? 0.03125F : 0.0625F);
        return new Matrix4f().translation(palm).rotate(rotation).translate(-palmX, -0.5625F, 0);
    }
}
