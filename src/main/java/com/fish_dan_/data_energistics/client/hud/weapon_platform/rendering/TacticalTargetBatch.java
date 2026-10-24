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
import org.jspecify.annotations.Nullable;

/** Screen overlap determines paint layers; each layer batches rectangles and font atlases across targets. */
final class TacticalTargetBatch implements MultiBufferSource, AutoCloseable {

    private final TacticalOverlapLayers overlaps = new TacticalOverlapLayers();
    private final ObjectArrayList<Layer> layers = new ObjectArrayList<>();
    private final ObjectArrayList<ByteBufferBuilder> textMemory = new ObjectArrayList<>();
    private final Object2ObjectLinkedOpenHashMap<RenderType, BufferBuilder> textBuilders = new Object2ObjectLinkedOpenHashMap<>();
    private int usedLayers;

    @Override
    public VertexConsumer getBuffer(RenderType type) {
        return textBuilders.computeIfAbsent(type, (RenderType key) -> {
            int slot = textBuilders.size();
            if (slot == textMemory.size()) textMemory.add(new ByteBufferBuilder(4096));
            return new BufferBuilder(textMemory.get(slot), key.mode(), key.format());
        });
    }

    void fill(GUIContext context, int left, int top, int right, int bottom, int color) {
        var pose = context.graphics.pose().last().pose();
        Layer layer = layer(overlaps.add(area(pose, left, top, right, bottom), false));
        var vertices = layer.rectangles;
        if (vertices == null) {
            vertices = layer.rectangles = new BufferBuilder(layer.memory, RenderType.gui().mode(), RenderType.gui().format());
        }
        vertices.addVertex(pose, right, bottom, 0).setColor(color);
        vertices.addVertex(pose, right, top, 0).setColor(color);
        vertices.addVertex(pose, left, top, 0).setColor(color);
        vertices.addVertex(pose, left, bottom, 0).setColor(color);
    }

    void annotate(Matrix4f pose, ObjectList<FormattedCharSequence> lines, int width, int color) {
        // Include glyph overhang and antialiasing when deciding which primitives may exchange paint order.
        int index = overlaps.add(area(pose, -4, -2, width + 4, lines.size() * 10 + 2), true);
        layer(index).annotations.add(new Annotation(new Matrix4f(pose), lines, color));
    }

    void draw(GUIContext context) {
        RenderSystem.disableDepthTest();
        try {
            for (int index = 0; index < usedLayers; index++) {
                Layer layer = layers.get(index);
                if (layer.rectangles != null) {
                    var mesh = layer.rectangles.build();
                    layer.rectangles = null;
                    if (mesh != null) RenderType.gui().draw(mesh);
                }
                for (var annotation : layer.annotations) {
                    for (int line = 0; line < annotation.lines().size(); line++) {
                        context.mc.font.drawInBatch(annotation.lines().get(line), 0, line * 10, annotation.color(), false,
                                annotation.pose(), this, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
                    }
                }
                int slot = 0;
                for (var entry : textBuilders.object2ObjectEntrySet()) {
                    ByteBufferBuilder memory = textMemory.get(slot++);
                    var mesh = entry.getValue().build();
                    if (mesh == null) continue;
                    RenderType type = entry.getKey();
                    if (type.sortOnUpload()) mesh.sortQuads(memory, RenderSystem.getVertexSorting());
                    type.draw(mesh);
                }
                textBuilders.clear();
                layer.annotations.clear();
            }
        } finally {
            RenderSystem.enableDepthTest();
        }
        overlaps.clear();
        usedLayers = 0;
    }

    private Layer layer(int index) {
        while (layers.size() <= index) layers.add(new Layer());
        usedLayers = Math.max(usedLayers, index + 1);
        return layers.get(index);
    }

    @Override
    public void close() {
        for (var layer : layers) layer.memory.close();
        layers.clear();
        textBuilders.clear();
        for (var memory : textMemory) memory.close();
        textMemory.clear();
        overlaps.clear();
        usedLayers = 0;
    }

    private static TacticalOverlapLayers.Area area(Matrix4f pose, float left, float top, float right, float bottom) {
        float leftX = pose.m00() * left, rightX = pose.m00() * right;
        float topX = pose.m10() * top, bottomX = pose.m10() * bottom;
        float leftY = pose.m01() * left, rightY = pose.m01() * right;
        float topY = pose.m11() * top, bottomY = pose.m11() * bottom;
        return new TacticalOverlapLayers.Area(Math.min(leftX, rightX) + Math.min(topX, bottomX) + pose.m30() - 1,
                Math.min(leftY, rightY) + Math.min(topY, bottomY) + pose.m31() - 1,
                Math.max(leftX, rightX) + Math.max(topX, bottomX) + pose.m30() + 1,
                Math.max(leftY, rightY) + Math.max(topY, bottomY) + pose.m31() + 1);
    }

    private record Annotation(Matrix4f pose, ObjectList<FormattedCharSequence> lines, int color) {}

    private static final class Layer {

        private final ByteBufferBuilder memory = new ByteBufferBuilder(1024);
        private final ObjectArrayList<Annotation> annotations = new ObjectArrayList<>();
        private @Nullable BufferBuilder rectangles;
    }
}
