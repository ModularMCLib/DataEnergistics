package com.fish_dan_.data_energistics.client.render.domain;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.Reference2ObjectLinkedOpenHashMap;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.function.Consumer;

/** Local-space mesh capture, independent of the camera used on subsequent frames. */
final class FrozenGeometry implements AutoCloseable {

    private record Layer(RenderType type, VertexBuffer vertices, int texture, Matrix4f textureMatrix) {}

    private final ObjectArrayList<Layer> layers = new ObjectArrayList<>();
    private final float shaderTime = RenderSystem.getShaderGameTime();

    FrozenGeometry(PoseStack poses, FrozenMaterialFrames materials, Consumer<MultiBufferSource> renderer) {
        var inversePose = new Matrix4f(poses.last().pose()).invert();
        var inverseNormal = new Matrix3f(poses.last().normal()).invert();
        var buffers = new Reference2ObjectLinkedOpenHashMap<RenderType, Capture>();
        try {
            renderer.accept(type -> buffers.computeIfAbsent(type, key -> new Capture(type, inversePose, inverseNormal)).consumer);
            for (var entry : buffers.reference2ObjectEntrySet()) {
                var mesh = entry.getValue().builder.build();
                if (mesh == null) continue;
                var type = entry.getKey();
                type.setupRenderState();
                try {
                    var vertices = new VertexBuffer(VertexBuffer.Usage.STATIC);
                    vertices.bind();
                    vertices.upload(mesh);
                    layers.add(new Layer(type, vertices, materials.texture(RenderSystem.getShaderTexture(0)), new Matrix4f(RenderSystem.getTextureMatrix())));
                } finally {
                    VertexBuffer.unbind();
                    type.clearRenderState();
                }
            }
        } catch (RuntimeException failure) {
            close();
            throw failure;
        } finally {
            buffers.values().forEach(capture -> capture.memory.close());
        }
    }

    void draw(PoseStack poses) {
        var matrix = new Matrix4f(RenderSystem.getModelViewMatrix()).mul(poses.last().pose());
        for (var layer : layers) {
            layer.type.setupRenderState();
            int original = RenderSystem.getShaderTexture(0);
            var textureMatrix = new Matrix4f(RenderSystem.getTextureMatrix());
            try {
                RenderSystem.setShaderTexture(0, layer.texture);
                RenderSystem.setTextureMatrix(layer.textureMatrix);
                var shader = RenderSystem.getShader();
                shader.setDefaultUniforms(layer.type.mode(), matrix, RenderSystem.getProjectionMatrix(), Minecraft.getInstance().getWindow());
                if (shader.GAME_TIME != null) shader.GAME_TIME.set(shaderTime);
                layer.vertices.bind();
                shader.apply();
                layer.vertices.draw();
                shader.clear();
            } finally {
                VertexBuffer.unbind();
                RenderSystem.setShaderTexture(0, original);
                RenderSystem.setTextureMatrix(textureMatrix);
                layer.type.clearRenderState();
            }
        }
    }

    @Override
    public void close() {
        layers.forEach(layer -> layer.vertices.close());
        layers.clear();
    }

    private static final class Capture {

        private final ByteBufferBuilder memory;
        private final BufferBuilder builder;
        private final VertexConsumer consumer;

        private Capture(RenderType type, Matrix4f inversePose, Matrix3f inverseNormal) {
            memory = new ByteBufferBuilder(type.bufferSize());
            builder = new BufferBuilder(memory, type.mode(), type.format());
            consumer = new VertexConsumer() {

                private final Vector3f vector = new Vector3f();

                @Override
                public VertexConsumer addVertex(float x, float y, float z) {
                    inversePose.transformPosition(x, y, z, vector);
                    builder.addVertex(vector.x, vector.y, vector.z);
                    return this;
                }

                @Override
                public VertexConsumer setColor(int red, int green, int blue, int alpha) {
                    builder.setColor(red, green, blue, alpha);
                    return this;
                }

                @Override
                public VertexConsumer setUv(float u, float v) {
                    builder.setUv(u, v);
                    return this;
                }

                @Override
                public VertexConsumer setUv1(int u, int v) {
                    builder.setUv1(u, v);
                    return this;
                }

                @Override
                public VertexConsumer setUv2(int u, int v) {
                    builder.setUv2(u, v);
                    return this;
                }

                @Override
                public VertexConsumer setNormal(float x, float y, float z) {
                    inverseNormal.transform(x, y, z, vector).normalize();
                    builder.setNormal(vector.x, vector.y, vector.z);
                    return this;
                }

                @Override
                public VertexConsumer misc(VertexFormatElement element, int... data) {
                    builder.misc(element, data);
                    return this;
                }
            };
        }
    }
}
