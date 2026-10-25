package com.fish_dan_.data_energistics.client.render.domain;

import com.fish_dan_.data_energistics.common.weapon.platform.domain.StasisDomain;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import org.joml.Matrix4f;

/** The four walls share a cached mesh; a height texture keeps their lower edges attached to the terrain. */
public final class DomainBoundaryMesh implements AutoCloseable {

    private static final double PATH_SPACING = 30.0 / (7 * 0.95 * 0.9);
    private final DomainBoundarySurface surface;
    private final VertexBuffer vertices = new VertexBuffer(VertexBuffer.Usage.STATIC);
    private final DynamicTexture heights;
    private final boolean empty;

    public DomainBoundaryMesh(ClientLevel level, StasisDomain domain) {
        surface = new DomainBoundarySurface(level, domain);
        heights = new DynamicTexture(surface.heightImage());
        heights.setFilter(true, false);
        try (var bytes = new ByteBufferBuilder(65536)) {
            var builder = new BufferBuilder(bytes, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL);
            for (Direction face : Direction.Plane.HORIZONTAL) {
                double height = surface.wallHeight;
                int paths = Mth.ceil((surface.width + height) / PATH_SPACING);
                for (int path = 0; path <= paths; path++) {
                    double intercept = path * PATH_SPACING - surface.width;
                    double phase = face.ordinal() * 9.7 + path * 5.3;
                    int color = 0xff000000 | (face.ordinal() * 37 + path * 59 & 255) << 16;
                    int slope = path % 4 == 0 ? -1 : 1;
                    double u = -4, v = slope == 1 ? intercept - 4 : height - intercept + 4;
                    double distance = phase;
                    for (int step = 0; u < surface.width; step++) {
                        double length = switch (step % 6) {
                            case 1, 3 -> 2;
                            case 5 -> 3;
                            default -> 7;
                        };
                        double nextU = u + length;
                        double nextV = v + slope * (step % 6 == 1 || step % 6 == 3 ? -length : length);
                        segment(builder, face, u, v, nextU, nextV, height, distance, color);
                        segment(builder, face, u, v - 0.38, nextU, nextV - 0.38, height, distance + 8, color & 0x00ffffff | 0x30000000);
                        distance += Math.hypot(nextU - u, nextV - v);
                        u = nextU;
                        v = nextV;
                    }
                }
            }
            var mesh = builder.build();
            empty = mesh == null;
            if (mesh != null) {
                try (mesh) {
                    vertices.bind();
                    vertices.upload(mesh);
                } finally {
                    VertexBuffer.unbind();
                }
            }
        }
    }

    private void segment(BufferBuilder builder, Direction face, double u, double v, double nextU, double nextV, double height, double distance, int color) {
        // Split at terrain-scale intervals so wall bottoms follow slopes without spanning entire hills.
        int steps = Mth.ceil(Math.hypot(nextU - u, nextV - v) / 2);
        double du = (nextU - u) / steps, dv = (nextV - v) / steps;
        double length = Math.hypot(du, dv), sideU = -dv / length * 0.6, sideV = du / length * 0.6;
        for (int step = 0; step < steps; step++) {
            double a = u + du * step, b = v + dv * step;
            double c = a + du, d = b + dv;
            double middleU = (a + c) / 2, middleV = (b + d) / 2;
            if (a < 0 || a > surface.width || c < 0 || c > surface.width || b < 0 || b > height || d < 0 || d > height || !surface.loaded(face, middleU, middleV)) continue;
            double start = distance + step * length;
            vertex(builder, face, surface.point(face, Mth.clamp(a + sideU, 0, surface.width), Mth.clamp(b + sideV, 0, height)), start, 0, color);
            vertex(builder, face, surface.point(face, Mth.clamp(a - sideU, 0, surface.width), Mth.clamp(b - sideV, 0, height)), start, 1, color);
            vertex(builder, face, surface.point(face, Mth.clamp(c - sideU, 0, surface.width), Mth.clamp(d - sideV, 0, height)), start + length, 1, color);
            vertex(builder, face, surface.point(face, Mth.clamp(c + sideU, 0, surface.width), Mth.clamp(d + sideV, 0, height)), start + length, 0, color);
        }
    }

    private static void vertex(BufferBuilder builder, Direction face, Vec3 position, double distance, float side, int color) {
        // Red carries a stable per-path motion phase; alpha controls the main ribbon and its faint echo.
        builder.addVertex((float) position.x, (float) position.y, (float) position.z).setColor(color).setUv((float) distance, side)
                .setNormal(face.getStepX(), face.getStepY(), face.getStepZ());
    }

    public boolean current(ClientLevel level) {
        return surface.current(level);
    }

    public AABB bounds() {
        return surface.bounds;
    }

    public Vec3 origin() {
        return surface.origin();
    }

    public void draw(Matrix4f view, Matrix4f projection, ShaderInstance shader) {
        shader.setSampler("SurfaceHeights", heights.getId());
        shader.safeGetUniform("DomainWidth").set((float) surface.width);
        shader.safeGetUniform("HeightRange").set((float) (surface.bounds.minY - surface.top + 20), (float) (surface.top - surface.bounds.minY));
        vertices.bind();
        vertices.drawWithShader(view, projection, shader);
    }

    public boolean empty() {
        return empty;
    }

    @Override
    public void close() {
        heights.close();
        vertices.close();
    }
}
