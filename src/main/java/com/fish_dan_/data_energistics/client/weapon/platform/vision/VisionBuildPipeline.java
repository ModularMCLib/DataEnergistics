package com.fish_dan_.data_energistics.client.weapon.platform.vision;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.utils.BoundedVirtualTaskQueue;
import com.fish_dan_.data_energistics.utils.vision.VisionGeometryBatch;
import com.fish_dan_.data_energistics.utils.vision.VisionGeometryBuilder;
import com.fish_dan_.data_energistics.utils.vision.VisionSectionSnapshot;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexBuffer;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import lombok.RequiredArgsConstructor;

import java.util.function.ObjLongConsumer;

/** Vision-specific staging over the shared task queue; all partial GPU batches belong to the render thread. */
final class VisionBuildPipeline {

    private final BoundedVirtualTaskQueue<VisionGeometryBatch> tasks;
    private final Long2ObjectOpenHashMap<Build> builds = new Long2ObjectOpenHashMap<>();
    private final LongOpenHashSet failed = new LongOpenHashSet();

    VisionBuildPipeline(String threadPrefix) {
        tasks = new BoundedVirtualTaskQueue<>(threadPrefix, 1, 8, VisionGeometryBatch::discard);
    }

    boolean hasCapacity() {
        return tasks.hasCapacity();
    }

    boolean contains(long section) {
        return builds.containsKey(section) || failed.contains(section);
    }

    void start(long section, VisionSectionSnapshot snapshot) {
        Build build = new Build(snapshot);
        builds.put(section, build);
        try {
            tasks.submit(section, work -> new VisionGeometryBuilder(snapshot, work::cancelled, work::emit).build());
        } catch (RuntimeException | Error failure) {
            builds.remove(section);
            throw failure;
        }
    }

    void upload(ObjLongConsumer<VisionSectionMesh> publish) {
        RenderSystem.assertOnRenderThread();
        long deadline = System.nanoTime() + 500_000;
        for (int batches = 0; batches < 2 && System.nanoTime() < deadline;) {
            var result = tasks.poll();
            if (result == null) break;
            Build build = builds.get(result.key());
            if (result.failure() != null) {
                cancel(result.key());
                failed.add(result.key());
                Data_Energistics.LOGGER.error("Failed to build platform vision section {}", result.key(), result.failure());
            } else if (result.value() != null) {
                var batch = result.value();
                try {
                    VertexBuffer buffer = batch.upload();
                    switch (batch.kind()) {
                        case THERMAL -> build.thermal.add(buffer);
                        case SONAR_SURFACE -> build.surfaces.add(buffer);
                        case SONAR_LINES -> build.lines.add(buffer);
                    }
                } catch (RuntimeException failure) {
                    cancel(result.key());
                    failed.add(result.key());
                    Data_Energistics.LOGGER.error("Failed to upload platform vision section {}", result.key(), failure);
                }
                batches++;
            } else {
                builds.remove(result.key());
                publish.accept(VisionSectionMesh.create(build.snapshot.origin(), build.snapshot.modes(), build.thermal, build.surfaces, build.lines), result.key());
            }
        }
    }

    void cancel(long section) {
        failed.remove(section);
        tasks.cancel(section);
        if (!builds.containsKey(section)) return;
        Build build = builds.remove(section);
        build.thermal.forEach(VertexBuffer::close);
        build.surfaces.forEach(VertexBuffer::close);
        build.lines.forEach(VertexBuffer::close);
    }

    void clear() {
        tasks.clear();
        for (long section : builds.keySet().toLongArray()) cancel(section);
        failed.clear();
    }

    @RequiredArgsConstructor
    private static final class Build {

        private final VisionSectionSnapshot snapshot;
        private final ObjectArrayList<VertexBuffer> thermal = new ObjectArrayList<>();
        private final ObjectArrayList<VertexBuffer> surfaces = new ObjectArrayList<>();
        private final ObjectArrayList<VertexBuffer> lines = new ObjectArrayList<>();
    }
}
