package com.fish_dan_.data_energistics.client.weapon.platform.vision;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.client.registry.DEShaders;
import com.fish_dan_.data_energistics.common.weapon.platform.model.PlatformAbility;
import com.fish_dan_.data_energistics.util.client.vision.VisionSectionCapture;
import com.fish_dan_.data_energistics.util.client.vision.VisionSectionSurvey;
import com.fish_dan_.data_energistics.util.client.vision.VisionShapeCache;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;

import com.mojang.blaze3d.vertex.VertexBuffer;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectList;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

/** Client-thread capture and cache lifecycle; pure geometry runs in the bounded virtual-thread pipeline. */
@EventBusSubscriber(modid = Data_Energistics.MODID, value = Dist.CLIENT)
public final class VisionSectionCache {

    public static final int SONAR_RADIUS = 48;
    public static final int SONAR_RADIUS_SQUARED = SONAR_RADIUS * SONAR_RADIUS;
    private static final Long2ObjectOpenHashMap<VisionSectionMesh> MESHES = new Long2ObjectOpenHashMap<>();
    private static final LongLinkedOpenHashSet PENDING = new LongLinkedOpenHashSet();
    private static final LongLinkedOpenHashSet BACKGROUND = new LongLinkedOpenHashSet();
    private static final LongLinkedOpenHashSet FAILED_CAPTURES = new LongLinkedOpenHashSet();
    private static final VisionBuildPipeline PIPELINE = new VisionBuildPipeline();
    private static final VisionShapeCache SHAPES = new VisionShapeCache();
    private static @Nullable ClientLevel world;
    private static @Nullable VisionSectionSurvey survey;
    private static @Nullable VisionSectionCapture capture;
    private static long captureKey;
    private static int modes;
    private static int surveyTicks;
    private static long centerSection = Long.MIN_VALUE;
    private static long[] prune = new long[0];
    private static int pruneCursor;

    private VisionSectionCache() {}

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft client = Minecraft.getInstance();
        int requested = (PlatformVisionState.active(PlatformAbility.THERMAL_IMAGING) ? VisionSectionMesh.THERMAL : 0) | (PlatformVisionState.active(PlatformAbility.SONAR) ? VisionSectionMesh.SONAR : 0);
        if (client.level == null || client.player == null || requested == 0) {
            clear();
            return;
        }
        if (world != client.level || modes != requested) {
            clear();
            world = client.level;
            modes = requested;
        }
        long deadline = System.nanoTime() + 3_000_000;
        Vec3 player = client.player.position();
        int distance = client.options.getEffectiveRenderDistance();
        long center = SectionPos.asLong(client.player.blockPosition());
        if (center != centerSection || survey == null && surveyTicks-- <= 0) {
            centerSection = center;
            survey = new VisionSectionSurvey(SectionPos.x(center), SectionPos.y(center), SectionPos.z(center), (modes & VisionSectionMesh.THERMAL) != 0 ? distance : 3);
            surveyTicks = 20;
            if (pruneCursor >= prune.length) {
                prune = MESHES.keySet().toLongArray();
                pruneCursor = 0;
            }
        }
        long pruneDeadline = Math.min(deadline, System.nanoTime() + 250_000);
        while (pruneCursor < prune.length && System.nanoTime() < pruneDeadline) {
            long key = prune[pruneCursor++];
            if (required(client.level, key, player, distance) == 0) remove(key);
        }
        long surveyDeadline = Math.min(deadline, System.nanoTime() + 750_000);
        while (survey != null && System.nanoTime() < surveyDeadline) {
            long key = survey.next(client.level, surveyDeadline);
            if (key != VisionSectionSurvey.NO_SECTION) schedule(client.level, key, player, distance);
            if (survey.isFinished()) survey = null;
        }
        while (PIPELINE.hasCapacity() && System.nanoTime() < deadline) {
            if (capture == null) {
                if (PENDING.isEmpty() && BACKGROUND.isEmpty()) break;
                captureKey = !PENDING.isEmpty() ? PENDING.removeFirstLong() : BACKGROUND.removeFirstLong();
                int required = required(client.level, captureKey, player, distance);
                if (required == 0) {
                    remove(captureKey);
                    continue;
                }
                if (PIPELINE.contains(captureKey) || FAILED_CAPTURES.contains(captureKey)) continue;
                capture = new VisionSectionCapture(captureKey, required);
            }
            try {
                var snapshot = capture.advance(client.level, SHAPES, deadline);
                if (snapshot == null) break;
                PIPELINE.start(captureKey, snapshot);
                capture = null;
            } catch (RuntimeException failure) {
                FAILED_CAPTURES.add(captureKey);
                capture = null;
                Data_Energistics.LOGGER.error("Failed to capture platform vision section {}", captureKey, failure);
            }
        }
    }

    private static void schedule(ClientLevel level, long key, Vec3 player, int distance) {
        int required = required(level, key, player, distance);
        if (required == 0 || PIPELINE.contains(key) || FAILED_CAPTURES.contains(key) || capture != null && captureKey == key) return;
        var chunk = level.getChunkSource().getChunk(SectionPos.x(key), SectionPos.z(key), false);
        if (chunk == null) return;
        var section = chunk.getSection(level.getSectionIndexFromSectionY(SectionPos.y(key)));
        if (MESHES.containsKey(key) && MESHES.get(key).modes() == required && !section.maybeHas(BlockState::hasDynamicLightEmission)) return;
        if ((required & VisionSectionMesh.SONAR) != 0) PENDING.add(key);
        else BACKGROUND.add(key);
    }

    private static int required(ClientLevel level, long key, Vec3 player, int viewDistance) {
        int y = SectionPos.sectionToBlockCoord(SectionPos.y(key));
        if (level.isOutsideBuildHeight(y)) return 0;
        var chunk = level.getChunkSource().getChunk(SectionPos.x(key), SectionPos.z(key), false);
        if (chunk == null || chunk.getSection(level.getSectionIndex(y)).hasOnlyAir()) return 0;
        long origin = origin(key);
        int result = 0;
        if ((modes & VisionSectionMesh.SONAR) != 0 && bounds(origin).distanceToSqr(player) <= SONAR_RADIUS_SQUARED) result |= VisionSectionMesh.SONAR;
        if ((modes & VisionSectionMesh.THERMAL) != 0 && Math.abs(BlockPos.getX(origin) - player.x) <= (viewDistance + 1) * 16 && Math.abs(BlockPos.getZ(origin) - player.z) <= (viewDistance + 1) * 16) result |= VisionSectionMesh.THERMAL;
        return result;
    }

    static void upload() {
        Minecraft client = Minecraft.getInstance();
        ClientLevel level = client.level;
        var player = client.player;
        if (level == null || level != world || player == null) return;
        PIPELINE.upload((mesh, key) -> {
            if (required(level, key, player.position(), client.options.getEffectiveRenderDistance()) != mesh.modes()) {
                mesh.close();
                return;
            }
            remove(key);
            MESHES.put(key, mesh);
        });
    }

    /** Cancels all stages before queuing a new revision, including updates received before a GPU mesh exists. */
    public static void invalidate(ClientLevel level, BlockPos position) {
        if (world != level) return;
        SHAPES.clear();
        for (int x = (position.getX() - 1) >> 4; x <= (position.getX() + 1) >> 4; x++)
            for (int y = (position.getY() - 1) >> 4; y <= (position.getY() + 1) >> 4; y++)
                for (int z = (position.getZ() - 1) >> 4; z <= (position.getZ() + 1) >> 4; z++) dirty(SectionPos.asLong(x, y, z));
    }

    private static void dirty(long key) {
        PIPELINE.cancel(key);
        FAILED_CAPTURES.remove(key);
        if (capture != null && captureKey == key) capture = null;
        BACKGROUND.remove(key);
        PENDING.addAndMoveToFirst(key);
    }

    @SubscribeEvent
    public static void loaded(ChunkEvent.Load event) {
        if (event.getLevel() == world) chunkChanged(event.getChunk().getPos());
    }

    @SubscribeEvent
    public static void unloaded(ChunkEvent.Unload event) {
        if (event.getLevel() == world) chunkChanged(event.getChunk().getPos());
    }

    private static void chunkChanged(ChunkPos chunk) {
        ClientLevel level = world;
        if (level == null) return;
        SHAPES.clear();
        survey = null;
        surveyTicks = 0;
        for (int x = chunk.x - 1; x <= chunk.x + 1; x++) for (int z = chunk.z - 1; z <= chunk.z + 1; z++) {
            LevelChunk neighboring = level.getChunkSource().getChunk(x, z, false);
            if (neighboring == null) continue;
            for (int index = 0; index < neighboring.getSections().length; index++) {
                if (neighboring.getSection(index).hasOnlyAir()) continue;
                dirty(SectionPos.asLong(x, level.getSectionYFromSectionIndex(index), z));
            }
        }
    }

    static void draw(RenderLevelStageEvent event, Vec3 player, boolean thermal) {
        var shader = DEShaders.getVisionSurface();
        var camera = event.getCamera().getPosition();
        shader.safeGetUniform("ScanRadiusSquared").set(thermal ? 0f : (float) SONAR_RADIUS_SQUARED);
        int viewBlocks = (Minecraft.getInstance().options.getEffectiveRenderDistance() + 1) * 16;
        try {
            for (var mesh : MESHES.values()) {
                if (!thermal && bounds(mesh.origin()).distanceToSqr(player) > SONAR_RADIUS_SQUARED) continue;
                if (thermal && (Math.abs(BlockPos.getX(mesh.origin()) - player.x) > viewBlocks || Math.abs(BlockPos.getZ(mesh.origin()) - player.z) > viewBlocks)) continue;
                var buffers = thermal ? mesh.thermal() : mesh.sonar();
                if (buffers.isEmpty() && (thermal || mesh.sonarSurface().isEmpty()) || !event.getFrustum().isVisible(bounds(mesh.origin()))) continue;
                int x = BlockPos.getX(mesh.origin()), y = BlockPos.getY(mesh.origin()), z = BlockPos.getZ(mesh.origin());
                shader.safeGetUniform("SectionOffset").set((float) (x - player.x), (float) (y - player.y), (float) (z - player.z));
                var view = new Matrix4f(event.getModelViewMatrix()).translate((float) (x - camera.x), (float) (y - camera.y), (float) (z - camera.z));
                if (!thermal) draw(mesh.sonarSurface(), view, event.getProjectionMatrix(), shader);
                draw(buffers, view, event.getProjectionMatrix(), shader);
            }
        } finally {
            VertexBuffer.unbind();
        }
    }

    private static void draw(ObjectList<VertexBuffer> buffers, Matrix4f view, Matrix4f projection, ShaderInstance shader) {
        for (var buffer : buffers) {
            buffer.bind();
            buffer.drawWithShader(view, projection, shader);
        }
    }

    private static void remove(long key) {
        if (MESHES.containsKey(key)) MESHES.remove(key).close();
    }

    private static long origin(long section) {
        return BlockPos.asLong(SectionPos.sectionToBlockCoord(SectionPos.x(section)), SectionPos.sectionToBlockCoord(SectionPos.y(section)), SectionPos.sectionToBlockCoord(SectionPos.z(section)));
    }

    private static AABB bounds(long origin) {
        int x = BlockPos.getX(origin), y = BlockPos.getY(origin), z = BlockPos.getZ(origin);
        return new AABB(x, y, z, x + 16, y + 16, z + 16);
    }

    public static void clear() {
        PIPELINE.clear();
        SHAPES.clear();
        MESHES.values().forEach(VisionSectionMesh::close);
        MESHES.clear();
        PENDING.clear();
        BACKGROUND.clear();
        FAILED_CAPTURES.clear();
        capture = null;
        survey = null;
        world = null;
        modes = 0;
        centerSection = Long.MIN_VALUE;
        surveyTicks = 0;
        prune = new long[0];
    }
}
