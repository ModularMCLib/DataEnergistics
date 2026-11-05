package com.fish_dan_.data_energistics.client.weapon.platform.vision;

import com.fish_dan_.data_energistics.util.client.vision.VisionSectionSnapshot;

import com.mojang.blaze3d.vertex.VertexBuffer;
import it.unimi.dsi.fastutil.objects.ObjectList;

/** Render-thread GPU batches. Published only after every batch in a section revision is uploaded. */
record VisionSectionMesh(long origin, int modes, ObjectList<VertexBuffer> thermal, ObjectList<VertexBuffer> sonarSurface, ObjectList<VertexBuffer> sonar) implements AutoCloseable {

    static final int THERMAL = VisionSectionSnapshot.THERMAL;
    static final int SONAR = VisionSectionSnapshot.SONAR;

    @Override
    public void close() {
        thermal.forEach(VertexBuffer::close);
        sonarSurface.forEach(VertexBuffer::close);
        sonar.forEach(VertexBuffer::close);
    }
}
