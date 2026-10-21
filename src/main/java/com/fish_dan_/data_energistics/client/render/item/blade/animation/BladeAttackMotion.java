package com.fish_dan_.data_energistics.client.render.item.blade.animation;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Numeric root tracks from FMIC heavy_sabre attack_1/2/3, retargeted to the grip of an upright blade. */
public final class BladeAttackMotion {

    public static final float DURATION_SECONDS = 0.7083F;
    public static final float DURATION_TICKS = DURATION_SECONDS * 20;
    private static final float RADIANS = (float) (Math.PI / 180);
    // In the reference rig, -X of the sw bone becomes the blade's +Y after its 11.25 degree cant.
    private static final float SOURCE_CANT = 11.25F * RADIANS;
    private static final Clip[] CLIPS = {
            new Clip(new Track(new Key[] {
                    key(0, 20.62983F, 22.07286F, 77.84381F),
                    curve(0.0417F, -20.97747F, 11.84374F, -14.75552F),
                    key(0.0833F, 39.96285F, 4.50035F, -43.4755F),
                    key(0.125F, 116.02109F, 4.04338F, -56.10993F),
                    key(0.1667F, 116.02109F, 4.04338F, -56.10993F),
                    curve(0.375F, 104.98177F, 3.47014F, -52.70725F),
                    curve(0.4583F, 47.83F, -2.28F, -19.41F),
                    key(0.7083F, 20.62983F, 22.07286F, 77.84381F)
            }), new Track(
                    new Key[] {
                            key(0, -5, 14, -5.775F), curve(0.0417F, -6, 21, 1),
                            curve(0.0833F, 7.31F, 14.25F, -6.75F),
                            key(0.125F, 13.2871F, 13.93239F, 3.93551F),
                            key(0.1667F, 13.2871F, 13.93239F, 3.93551F),
                            curve(0.375F, 10.4375F, 14, 2), curve(0.5F, -4.145F, 15.63F, -3.01F),
                            key(0.7083F, -5, 14, -5.775F)
                    })),
            new Clip(new Track(new Key[] {
                    key(0, 20.62983F, 22.07286F, 77.84381F),
                    curve(0.0417F, -13.73789F, -19.88226F, 37.27847F),
                    curve(0.0833F, 54.45317F, -12.51321F, 44.29608F),
                    key(0.125F, 114.40641F, 11.99238F, 43.98441F),
                    key(0.1667F, 114.35773F, 12.36753F, 42.68644F),
                    curve(0.375F, 108.54123F, 10.89566F, 41.61601F),
                    curve(0.4583F, 61.08F, -2.28F, 10.715F),
                    key(0.7083F, 20.62983F, 22.07286F, 77.84381F)
            }), new Track(
                    new Key[] {
                            key(0, -5, 14, -5.775F), curve(0.0417F, 5.47555F, 17.0483F, 3.24769F),
                            curve(0.0833F, 0.64F, 14.9F, -7.8F), key(0.125F, -11.25F, 3.75F, 5),
                            key(0.1667F, -10.25F, 7.75F, 5), curve(0.375F, -10.25F, 7.75F, 5),
                            curve(0.5F, -6.145F, 15.63F, -1.4475F), key(0.7083F, -5, 14, -5.775F)
                    })),
            new Clip(new Track(new Key[] {
                    key(0, 20.62983F, 22.07286F, 77.84381F),
                    curve(0.0417F, 46.32005F, -29.8742F, -148.23983F),
                    curve(0.0833F, 95.38265F, -9.30811F, -143.51743F),
                    key(0.125F, 136.20046F, -9.89994F, -135.76368F),
                    key(0.1667F, 136.20046F, -9.89994F, -135.76368F),
                    curve(0.375F, 114.98177F, -11.52986F, -124.70725F),
                    curve(0.4583F, 47.83F, -2.28F, -19.41F),
                    key(0.7083F, 20.62983F, 22.07286F, 77.84381F)
            }), new Track(
                    new Key[] {
                            key(0, -5, 14, -5.775F), curve(0.0417F, -10.62303F, 9.43123F, -6.2203F),
                            curve(0.0833F, 3.6369F, 17.6565F, -1.19349F),
                            key(0.125F, 9.09879F, 22.6536F, 1.2662F),
                            key(0.1667F, 9.09879F, 22.6536F, 1.2662F),
                            curve(0.2917F, 8.9F, 21.92F, 1.39F), curve(0.375F, 6.4375F, 18, 1),
                            curve(0.5F, -4.145F, 15.63F, -3.01F), key(0.7083F, -5, 14, -5.775F)
                    }))
    };

    private BladeAttackMotion() {}

    /** Returns a grip-local rigid transform; the caller applies its existing, correctly oriented hand pose first. */
    public static Matrix4f sample(int combo, float seconds) {
        if (combo < 0 || combo >= CLIPS.length || !Float.isFinite(seconds)) throw new IllegalArgumentException("Invalid blade attack sample");
        if (seconds <= 0 || seconds >= DURATION_SECONDS) return new Matrix4f();
        Clip clip = CLIPS[combo];
        Quaternionf neutralInverse = rotation(new Vector3f(20.62983F, 22.07286F, 77.84381F)).invert();
        Quaternionf basisInverse = new Quaternionf().rotationX(-SOURCE_CANT).mul(neutralInverse);
        Vector3f position = clip.position.sample(seconds).sub(-5, 14, -5.775F).div(16);
        basisInverse.transform(position);
        Quaternionf rotation = new Quaternionf(basisInverse).mul(rotation(clip.rotation.sample(seconds))).rotateX(SOURCE_CANT);
        return new Matrix4f().translation(position).rotate(rotation);
    }

    private static Quaternionf rotation(Vector3f angles) {
        return new Quaternionf().rotationZYX(angles.z * RADIANS, angles.y * RADIANS, angles.x * RADIANS);
    }

    private static Key key(float time, float x, float y, float z) {
        return new Key(time, x, y, z, false);
    }

    private static Key curve(float time, float x, float y, float z) {
        return new Key(time, x, y, z, true);
    }

    private record Clip(Track rotation, Track position) {}

    private record Key(float time, float x, float y, float z, boolean smooth) {}

    private record Track(Key[] keys) {

        Vector3f sample(float time) {
            int index = 0;
            while (index < keys.length - 2 && time >= keys[index + 1].time) index++;
            Key start = keys[index];
            Key end = keys[index + 1];
            float fraction = (time - start.time) / (end.time - start.time);
            Key before = keys[Math.max(0, index - 1)];
            Key after = keys[Math.min(keys.length - 1, index + 2)];
            boolean smooth = start.smooth || end.smooth;
            return new Vector3f(interpolate(before.x, start.x, end.x, after.x, fraction, smooth),
                    interpolate(before.y, start.y, end.y, after.y, fraction, smooth),
                    interpolate(before.z, start.z, end.z, after.z, fraction, smooth));
        }

        private static float interpolate(float before, float start, float end, float after, float fraction, boolean smooth) {
            if (start == end) return start;
            if (!smooth) return start + (end - start) * fraction;
            float squared = fraction * fraction;
            return 0.5F * (2 * start + (end - before) * fraction + (2 * before - 5 * start + 4 * end - after) * squared + (-before + 3 * start - 3 * end + after) * squared * fraction);
        }
    }
}
