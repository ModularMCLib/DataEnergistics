package com.fish_dan_.data_energistics.client.render.item.sanctifier;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Right-upper preparation followed by a forward, left-lower cut; model +Z always leads the cutting stroke. */
public final class SanctifierExecutionPose {

    private SanctifierExecutionPose() {}

    /** Samples camera coordinates (+X right, +Y up, -Z forward) for either first-person or world rendering. */
    public static Matrix4f sample(float tick, float scale) {
        Matrix4f rest = new Matrix4f().translation(0.46625F, -0.55125F, -1.00125F)
                .rotateXYZ((float) Math.toRadians(-30), (float) Math.PI, (float) Math.toRadians(10)).scale(scale);
        Matrix4f raised = cut(0.1F, 0, scale);
        if (tick < 12) return blend(rest, raised, smooth(tick / 12), scale);
        if (tick < 18) return raised;
        if (tick < 26) {
            float progress = smooth((tick - 18) / 8);
            return cut(0.1F + 2.7F * progress, progress, scale);
        }
        return blend(cut(2.8F, 1, scale), rest, smooth((tick - 26) / 4), scale);
    }

    private static Matrix4f cut(float angle, float progress, float scale) {
        float diagonal = (float) Math.sqrt(0.5);
        float cosine = (float) Math.cos(angle);
        float sine = (float) Math.sin(angle);
        Vector3f blade = new Vector3f(diagonal * cosine, diagonal * cosine, -sine);
        Vector3f edge = new Vector3f(-diagonal * sine, -diagonal * sine, -cosine);
        Vector3f thickness = new Vector3f(blade).cross(edge);
        return new Matrix4f().m00(thickness.x).m01(thickness.y).m02(thickness.z)
                .m10(blade.x).m11(blade.y).m12(blade.z)
                .m20(edge.x).m21(edge.y).m22(edge.z)
                .setTranslation(0.52F - 0.8F * progress, 0.04F - 0.66F * progress, -0.95F - 0.17F * progress)
                .scale(scale);
    }

    private static Matrix4f blend(Matrix4f start, Matrix4f end, float progress, float scale) {
        Vector3f position = start.getTranslation(new Vector3f()).lerp(end.getTranslation(new Vector3f()), progress);
        Quaternionf rotation = start.getUnnormalizedRotation(new Quaternionf()).slerp(end.getUnnormalizedRotation(new Quaternionf()), progress);
        return new Matrix4f().translation(position).rotate(rotation).scale(scale);
    }

    private static float smooth(float value) {
        float bounded = Math.clamp(value, 0, 1);
        return bounded * bounded * (3 - 2 * bounded);
    }
}
