package com.fish_dan_.data_energistics.utils.vision;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexBuffer;

/** One bounded CPU batch. Queue publication transfers both mesh and native allocator to the render thread. */
@OnlyIn(Dist.CLIENT)
public record VisionGeometryBatch(Kind kind, ByteBufferBuilder bytes, MeshData mesh) {

    public enum Kind {
        THERMAL,
        SONAR_SURFACE,
        SONAR_LINES
    }

    public VertexBuffer upload() {
        VertexBuffer buffer;
        try {
            buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
        } catch (RuntimeException | Error failure) {
            discard();
            throw failure;
        }
        try {
            buffer.bind();
            buffer.upload(mesh);
            return buffer;
        } catch (RuntimeException | Error failure) {
            buffer.close();
            throw failure;
        } finally {
            VertexBuffer.unbind();
            bytes.close();
        }
    }

    public void discard() {
        mesh.close();
        bytes.close();
    }
}
