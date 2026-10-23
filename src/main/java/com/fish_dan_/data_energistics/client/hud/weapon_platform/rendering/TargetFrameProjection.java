package com.fish_dan_.data_energistics.client.hud.weapon_platform.rendering;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;

/** Captures the actual world camera, including its projection, for the subsequent LDLib2 HUD pass. */
public final class TargetFrameProjection {

    private static final Matrix4f PROJECTION = new Matrix4f();
    private static Vec3 camera = Vec3.ZERO;

    private TargetFrameProjection() {}

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

    public record Bounds(int left, int top, int right, int bottom) {}
}
