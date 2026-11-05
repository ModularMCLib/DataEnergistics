package com.fish_dan_.data_energistics.client.weapon.platform.vision;

import com.fish_dan_.data_energistics.util.client.vision.VisionSectionSnapshot;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

import com.mojang.blaze3d.vertex.VertexBuffer;
import it.unimi.dsi.fastutil.objects.ObjectList;

/** Render-thread GPU batches. Published only after every batch in a section revision is uploaded. */
record VisionSectionMesh(long origin, int modes, AABB bounds, ObjectList<VertexBuffer> thermal, ObjectList<VertexBuffer> sonarSurface, ObjectList<VertexBuffer> sonar) implements AutoCloseable {

    static final int THERMAL = VisionSectionSnapshot.THERMAL;
    static final int SONAR = VisionSectionSnapshot.SONAR;

    static VisionSectionMesh create(long origin, int modes, ObjectList<VertexBuffer> thermal, ObjectList<VertexBuffer> sonarSurface, ObjectList<VertexBuffer> sonar) {
        int x = BlockPos.getX(origin), y = BlockPos.getY(origin), z = BlockPos.getZ(origin);
        return new VisionSectionMesh(origin, modes, new AABB(x, y, z, x + 16, y + 16, z + 16), thermal, sonarSurface, sonar);
    }

    @Override
    public void close() {
        thermal.forEach(VertexBuffer::close);
        sonarSurface.forEach(VertexBuffer::close);
        sonar.forEach(VertexBuffer::close);
    }
}
