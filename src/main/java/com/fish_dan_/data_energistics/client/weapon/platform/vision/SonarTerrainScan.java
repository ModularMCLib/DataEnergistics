package com.fish_dan_.data_energistics.client.weapon.platform.vision;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.util.client.vision.VisionSectionCapture;
import com.fish_dan_.data_energistics.util.client.vision.VisionSectionSurvey;
import com.fish_dan_.data_energistics.util.client.vision.VisionShapeCache;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import it.unimi.dsi.fastutil.bytes.ByteArrayList;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jspecify.annotations.Nullable;

/** Client-thread scan state. Block changes affect the next sweep; only pure mesh assembly leaves this thread. */
final class SonarTerrainScan {

    private final VisionBuildPipeline pipeline = new VisionBuildPipeline("de-sonar-");
    private final Long2ObjectOpenHashMap<VisionSectionMesh> meshes = new Long2ObjectOpenHashMap<>();
    private final LongLinkedOpenHashSet candidates = new LongLinkedOpenHashSet();
    private final LongLinkedOpenHashSet pending = new LongLinkedOpenHashSet();
    private final ObjectArrayList<VisionSectionMesh> retired = new ObjectArrayList<>();
    private @Nullable VisionSectionSurvey survey;
    private @Nullable VisionSectionCapture capture;
    private long captureKey;
    private Vec3 origin = Vec3.ZERO;
    private double radiusSquared;
    private boolean initialized;

    void update(Vec3 scanOrigin, double scanRadiusSquared, boolean started) {
        if (started || !initialized) {
            pipeline.clear();
            retired.addAll(meshes.values());
            meshes.clear();
            candidates.clear();
            pending.clear();
            capture = null;
            origin = scanOrigin;
            long center = SectionPos.asLong(BlockPos.containing(origin));
            survey = new VisionSectionSurvey(SectionPos.x(center), SectionPos.y(center), SectionPos.z(center), 3);
            initialized = true;
        }
        radiusSquared = scanRadiusSquared;
    }

    void tick(ClientLevel level, VisionShapeCache shapes, long deadline) {
        if (!initialized) return;
        long surveyDeadline = Math.min(deadline, System.nanoTime() + 200_000);
        while (survey != null && System.nanoTime() < surveyDeadline) {
            long key = survey.next(level, surveyDeadline);
            if (key != VisionSectionSurvey.NO_SECTION && distanceSquared(key) <= VisionSectionCache.SONAR_RADIUS_SQUARED) candidates.add(key);
            if (survey.isFinished()) survey = null;
        }
        var iterator = candidates.iterator();
        while (iterator.hasNext() && System.nanoTime() < deadline) {
            long key = iterator.nextLong();
            if (distanceSquared(key) <= radiusSquared) {
                iterator.remove();
                pending.add(key);
            }
        }
        while (pipeline.hasCapacity() && System.nanoTime() < deadline) {
            if (capture == null) {
                if (pending.isEmpty()) break;
                captureKey = pending.removeFirstLong();
                if (!loaded(level, captureKey)) continue;
                capture = new VisionSectionCapture(captureKey, VisionSectionMesh.SONAR);
            }
            try {
                var snapshot = capture.advance(level, shapes, deadline);
                if (snapshot == null) break;
                pipeline.start(captureKey, snapshot);
                capture = null;
            } catch (RuntimeException failure) {
                capture = null;
                Data_Energistics.LOGGER.error("Failed to capture sonar terrain section {} at scan origin {}", captureKey, origin, failure);
            }
        }
    }

    void upload(ClientLevel level) {
        // Old sweeps can contain hundreds of VBOs; reclaim them across frames instead of deleting them in one pulse.
        for (int count = 0; count < 2 && !retired.isEmpty(); count++) retired.removeLast().close();
        pipeline.upload((mesh, key) -> {
            if (loaded(level, key)) meshes.put(key, mesh);
            else mesh.close();
        });
    }

    void collectVisible(RenderLevelStageEvent event, Vec3 player, ObjectArrayList<VisionSectionMesh> drawMeshes, ByteArrayList drawModes) {
        for (var mesh : meshes.values()) {
            if (mesh.sonar().isEmpty() && mesh.sonarSurface().isEmpty()) continue;
            if (mesh.bounds().distanceToSqr(player) > VisionSectionCache.SONAR_RADIUS_SQUARED || !event.getFrustum().isVisible(mesh.bounds())) continue;
            drawMeshes.add(mesh);
            drawModes.add((byte) VisionSectionMesh.SONAR);
        }
    }

    void unload(ChunkPos chunk) {
        int centerY = SectionPos.blockToSectionCoord(origin.y);
        for (int y = centerY - 3; y <= centerY + 3; y++) {
            long key = SectionPos.asLong(chunk.x, y, chunk.z);
            candidates.remove(key);
            pending.remove(key);
            pipeline.cancel(key);
            if (capture != null && captureKey == key) capture = null;
        }
        var iterator = meshes.long2ObjectEntrySet().fastIterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (SectionPos.x(entry.getLongKey()) != chunk.x || SectionPos.z(entry.getLongKey()) != chunk.z) continue;
            retired.add(entry.getValue());
            iterator.remove();
        }
    }

    private double distanceSquared(long section) {
        int x = SectionPos.sectionToBlockCoord(SectionPos.x(section));
        int y = SectionPos.sectionToBlockCoord(SectionPos.y(section));
        int z = SectionPos.sectionToBlockCoord(SectionPos.z(section));
        double dx = Math.max(Math.max(x - origin.x, 0), origin.x - x - 16);
        double dy = Math.max(Math.max(y - origin.y, 0), origin.y - y - 16);
        double dz = Math.max(Math.max(z - origin.z, 0), origin.z - z - 16);
        return dx * dx + dy * dy + dz * dz;
    }

    private static boolean loaded(ClientLevel level, long section) {
        var chunk = level.getChunkSource().getChunk(SectionPos.x(section), SectionPos.z(section), false);
        return chunk != null && !chunk.getSection(level.getSectionIndexFromSectionY(SectionPos.y(section))).hasOnlyAir();
    }

    void clear() {
        pipeline.clear();
        meshes.values().forEach(VisionSectionMesh::close);
        retired.forEach(VisionSectionMesh::close);
        meshes.clear();
        retired.clear();
        candidates.clear();
        pending.clear();
        survey = null;
        capture = null;
        initialized = false;
    }
}
