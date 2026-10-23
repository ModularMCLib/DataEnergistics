package com.fish_dan_.data_energistics.client.hud.weapon_platform.rendering;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;

/** Captures the actual world camera, including its projection, for the subsequent LDLib2 HUD pass. */
public final class TacticalProjection {

    private static final Matrix4f PROJECTION = new Matrix4f();
    private static Vec3 camera = Vec3.ZERO;

    private TacticalProjection() {}

    public static void capture(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) return;
        PROJECTION.set(event.getProjectionMatrix()).mul(event.getModelViewMatrix());
        camera = event.getCamera().getPosition();
    }

    public static @Nullable Bounds project(AABB box, int width, int height) {
        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY;
        Vector4f center = new Vector4f((float) (box.getCenter().x - camera.x), (float) (box.getCenter().y - camera.y), (float) (box.getCenter().z - camera.z), 1).mul(PROJECTION);
        if (center.w <= 0) return null;
        for (int corner = 0; corner < 8; corner++) {
            var point = new Vector4f((float) ((corner & 1) == 0 ? box.minX - camera.x : box.maxX - camera.x),
                    (float) ((corner & 2) == 0 ? box.minY - camera.y : box.maxY - camera.y),
                    (float) ((corner & 4) == 0 ? box.minZ - camera.z : box.maxZ - camera.z), 1).mul(PROJECTION);
            if (point.w <= 0) continue;
            float x = (point.x / point.w + 1) * width / 2;
            float y = (1 - point.y / point.w) * height / 2;
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
        }
        if (maxX < 0 || minX > width || maxY < 0 || minY > height) return null;
        return new Bounds(Math.max(0, Math.round(minX)), Math.max(0, Math.round(minY)), Math.min(width - 1, Math.round(maxX)), Math.min(height - 1, Math.round(maxY)));
    }

    /** Clips in homogeneous camera space so guides crossing the near plane never jump across the screen. */
    public static @Nullable Line project(Vec3 from, Vec3 to, int width, int height) {
        Vector4f a = new Vector4f((float) (from.x - camera.x), (float) (from.y - camera.y), (float) (from.z - camera.z), 1).mul(PROJECTION);
        Vector4f b = new Vector4f((float) (to.x - camera.x), (float) (to.y - camera.y), (float) (to.z - camera.z), 1).mul(PROJECTION);
        float start = 0, end = 1;
        for (int plane = 0; plane < 6; plane++) {
            int component = plane / 2;
            float sign = (plane & 1) == 0 ? 1 : -1;
            float first = a.w + sign * a.get(component), second = b.w + sign * b.get(component);
            if (first < 0 && second < 0) return null;
            if ((first < 0) != (second < 0)) {
                float fraction = first / (first - second);
                if (first < 0) start = Math.max(start, fraction);
                else end = Math.min(end, fraction);
            }
        }
        if (start > end) return null;
        Vector4f first = a.lerp(b, start, new Vector4f()), second = a.lerp(b, end, new Vector4f());
        if (first.w <= 0 || second.w <= 0) return null;
        return new Line(new Vector2f((first.x / first.w + 1) * width / 2, (1 - first.y / first.w) * height / 2),
                new Vector2f((second.x / second.w + 1) * width / 2, (1 - second.y / second.w) * height / 2));
    }

    public static @Nullable Vector2f project(Vec3 point, int width, int height) {
        Vector4f projected = new Vector4f((float) (point.x - camera.x), (float) (point.y - camera.y), (float) (point.z - camera.z), 1).mul(PROJECTION);
        if (projected.w <= 0 || Math.abs(projected.x) > projected.w || Math.abs(projected.y) > projected.w || Math.abs(projected.z) > projected.w) return null;
        return new Vector2f((projected.x / projected.w + 1) * width / 2, (1 - projected.y / projected.w) * height / 2);
    }

    public record Line(Vector2f from, Vector2f to) {}

    public record Bounds(int left, int top, int right, int bottom) {}
}
