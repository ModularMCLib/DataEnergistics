package com.fish_dan_.data_energistics.client.hud.weapon_platform.rendering;

import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;

import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.FormattedCharSequence;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import org.joml.Matrix4f;

/** Render-thread batches bounded by one target; overlapping primitives retain their original paint order. */
final class TacticalTargetBatch implements MultiBufferSource, AutoCloseable {

    private final ObjectArrayList<ByteBufferBuilder> memory = new ObjectArrayList<>();
    private final Object2ObjectLinkedOpenHashMap<RenderType, BufferBuilder> builders = new Object2ObjectLinkedOpenHashMap<>();
    private final ObjectArrayList<Annotation> annotations = new ObjectArrayList<>();

    @Override
    public VertexConsumer getBuffer(RenderType type) {
        return builders.computeIfAbsent(type, (RenderType key) -> {
            int slot = builders.size();
            if (slot == memory.size()) memory.add(new ByteBufferBuilder(4096));
            return new BufferBuilder(memory.get(slot), key.mode(), key.format());
        });
    }

    void fill(GUIContext context, int left, int top, int right, int bottom, int color) {
        var pose = context.graphics.pose().last().pose();
        if (!annotations.isEmpty()) {
            Bounds area = bounds(pose, left, top, right, bottom);
            for (var annotation : annotations) {
                if (area.intersects(annotation.bounds())) {
                    draw(context);
                    break;
                }
            }
        }
        var vertices = context.graphics.bufferSource().getBuffer(RenderType.gui());
        vertices.addVertex(pose, right, bottom, 0).setColor(color);
        vertices.addVertex(pose, right, top, 0).setColor(color);
        vertices.addVertex(pose, left, top, 0).setColor(color);
        vertices.addVertex(pose, left, bottom, 0).setColor(color);
    }

    void annotate(Matrix4f pose, ObjectList<FormattedCharSequence> lines, int width, int color) {
        // Include glyph overhang so screen-edge clamping cannot move a later background over deferred text.
        annotations.add(new Annotation(new Matrix4f(pose), lines, bounds(pose, -4, -2, width + 4, lines.size() * 10 + 2), color));
    }

    void draw(GUIContext context) {
        if (annotations.isEmpty()) return;
        context.graphics.flush();
        for (var annotation : annotations) {
            for (int line = 0; line < annotation.lines().size(); line++) {
                context.mc.font.drawInBatch(annotation.lines().get(line), 0, line * 10, annotation.color(), false,
                        annotation.pose(), this, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
            }
        }
        RenderSystem.disableDepthTest();
        try {
            int slot = 0;
            for (var entry : builders.object2ObjectEntrySet()) {
                ByteBufferBuilder buffer = memory.get(slot++);
                var mesh = entry.getValue().build();
                if (mesh == null) continue;
                RenderType type = entry.getKey();
                if (type.sortOnUpload()) mesh.sortQuads(buffer, RenderSystem.getVertexSorting());
                type.draw(mesh);
            }
        } finally {
            RenderSystem.enableDepthTest();
        }
        builders.clear();
        annotations.clear();
    }

    @Override
    public void close() {
        annotations.clear();
        builders.clear();
        for (var buffer : memory) buffer.close();
        memory.clear();
    }

    private static Bounds bounds(Matrix4f pose, float left, float top, float right, float bottom) {
        float leftX = pose.m00() * left, rightX = pose.m00() * right;
        float topX = pose.m10() * top, bottomX = pose.m10() * bottom;
        float leftY = pose.m01() * left, rightY = pose.m01() * right;
        float topY = pose.m11() * top, bottomY = pose.m11() * bottom;
        return new Bounds(Math.min(leftX, rightX) + Math.min(topX, bottomX) + pose.m30(),
                Math.min(leftY, rightY) + Math.min(topY, bottomY) + pose.m31(),
                Math.max(leftX, rightX) + Math.max(topX, bottomX) + pose.m30(),
                Math.max(leftY, rightY) + Math.max(topY, bottomY) + pose.m31());
    }

    private record Bounds(float left, float top, float right, float bottom) {

        boolean intersects(Bounds other) {
            return left < other.right && right > other.left && top < other.bottom && bottom > other.top;
        }
    }

    private record Annotation(Matrix4f pose, ObjectList<FormattedCharSequence> lines, Bounds bounds, int color) {}
}
