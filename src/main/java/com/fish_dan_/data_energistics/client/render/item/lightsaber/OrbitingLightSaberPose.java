package com.fish_dan_.data_energistics.client.render.item.lightsaber;

import com.fish_dan_.data_energistics.common.item.powered.lightsaber.LightSaberOrbitGeometry;

import org.joml.Matrix4f;

/** Maps the saber's tip (+Y) and cutting edge (+Z) onto the orbit's radial and forward directions. */
final class OrbitingLightSaberPose {

    private OrbitingLightSaberPose() {}

    /** Accepts the ring's signed angle; the upper ring reverses time direction and cutting-edge orientation. */
    static Matrix4f itemTransform(float angleDegrees, int ring) {
        float radians = (float) Math.toRadians(angleDegrees);
        float cosine = (float) Math.cos(radians);
        float sine = (float) Math.sin(radians);
        float direction = ring == 0 ? 1 : -1;
        // Reversing both the normal and tangent retains a right-handed orthonormal frame.
        // The tangent is the derivative of the entity's position, so the edge leads the back.
        return new Matrix4f()
                .m00(0).m01(direction).m02(0)
                .m10(cosine).m11(0).m12(-sine)
                .m20(-sine * direction).m21(0).m22(-cosine * direction)
                .m30(LightSaberOrbitGeometry.RADIUS * cosine)
                .m31(LightSaberOrbitGeometry.HEIGHT + ring * LightSaberOrbitGeometry.RING_SEPARATION)
                .m32(-LightSaberOrbitGeometry.RADIUS * sine)
                .scale(LightSaberOrbitGeometry.SCALE);
    }
}
