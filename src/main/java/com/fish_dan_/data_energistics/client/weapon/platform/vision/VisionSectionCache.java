package com.fish_dan_.data_energistics.client.weapon.platform.vision;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.client.registry.DEShaders;
import com.fish_dan_.data_energistics.common.weapon.platform.model.PlatformAbility;
import com.fish_dan_.data_energistics.utils.vision.SonarPulse;
import com.fish_dan_.data_energistics.utils.vision.VisionSectionCapture;
import com.fish_dan_.data_energistics.utils.vision.VisionSectionSurvey;
import com.fish_dan_.data_energistics.utils.vision.VisionShapeCache;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;

import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import it.unimi.dsi.fastutil.bytes.ByteArrayList;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

/** Client-thread capture and cache lifecycle; pure geometry runs in the bounded virtual-thread pipeline. */
@EventBusSubscriber(modid = Data_Energistics.MOD_ID, value = Dist.CLIENT)
public final class VisionSectionCache {

    public static final int SONAR_RADIUS = SonarPulse.RADIUS;
    public static final int SONAR_RADIUS_SQUARED = SONAR_RADIUS * SONAR_RADIUS;
    private static final long TICK_BUDGET_NANOS = 1_500_000;
    private static final long PRUNE_BUDGET_NANOS = 100_000;
    private static final long SURVEY_BUDGET_NANOS = 350_000;
    private static final Long2ObjectOpenHashMap<VisionSectionMesh> MESHES = new Long2ObjectOpenHashMap<>();
    private static final LongLinkedOpenHashSet PENDING = new LongLinkedOpenHashSet();
    private static final LongLinkedOpenHashSet BACKGROUND = new LongLinkedOpenHashSet();
    private static final LongLinkedOpenHashSet FAILED_CAPTURES = new LongLinkedOpenHashSet();
    private static final VisionBuildPipeline PIPELINE = new VisionBuildPipeline("de-thermal-");
    private static final SonarTerrainScan SONAR = new SonarTerrainScan();
    private static final VisionShapeCache SHAPES = new VisionShapeCache();
    private static final ObjectArrayList<VisionSectionMesh> DRAW_MESHES = new ObjectArrayList<>();
    private static final ByteArrayList DRAW_MODES = new ByteArrayList();
    private static final Matrix4f DRAW_MATRIX = new Matrix4f();
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
            if (world != null) clear();
            return;
        }
        if (world != client.level || modes != requested) {
            clear();
            world = client.level;
            modes = requested;
        }
        long deadline = System.nanoTime() + TICK_BUDGET_NANOS;
        Vec3 player = client.player.position();
        int distance = client.options.getEffectiveRenderDistance();
        long center = SectionPos.asLong(client.player.blockPosition());
        boolean thermal = (modes & VisionSectionMesh.THERMAL) != 0;
        if ((modes & VisionSectionMesh.SONAR) != 0) {
            SONAR.tick(client.level, SHAPES, thermal ? Math.min(deadline, System.nanoTime() + 750_000) : deadline);
        }
        boolean thermalRescan = thermal && (center != centerSection || survey == null && surveyTicks-- <= 0);
        if (thermalRescan) {
            centerSection = center;
            survey = new VisionSectionSurvey(SectionPos.x(center), SectionPos.y(center), SectionPos.z(center), distance);
            surveyTicks = 20;
            if (pruneCursor >= prune.length) {
                prune = MESHES.keySet().toLongArray();
                pruneCursor = 0;
            }
        }
        long pruneDeadline = Math.min(deadline, System.nanoTime() + PRUNE_BUDGET_NANOS);
        while (pruneCursor < prune.length && System.nanoTime() < pruneDeadline) {
            long key = prune[pruneCursor++];
            if (required(client.level, key, player, distance) == 0) remove(key);
        }
        long surveyDeadline = Math.min(deadline, System.nanoTime() + SURVEY_BUDGET_NANOS);
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
        BACKGROUND.add(key);
    }

    private static int required(ClientLevel level, long key, Vec3 player, int viewDistance) {
        if ((modes & VisionSectionMesh.THERMAL) == 0) return 0;
        int y = SectionPos.sectionToBlockCoord(SectionPos.y(key));
        if (level.isOutsideBuildHeight(y)) return 0;
        var chunk = level.getChunkSource().getChunk(SectionPos.x(key), SectionPos.z(key), false);
        if (chunk == null || chunk.getSection(level.getSectionIndex(y)).hasOnlyAir()) return 0;
        long origin = origin(key);
        int result = 0;
        if (Math.abs(BlockPos.getX(origin) - player.x) <= (viewDistance + 1) * 16 && Math.abs(BlockPos.getZ(origin) - player.z) <= (viewDistance + 1) * 16) result = VisionSectionMesh.THERMAL;
        return result;
    }

    static void upload() {
        Minecraft client = Minecraft.getInstance();
        ClientLevel level = client.level;
        var player = client.player;
        if (level == null || level != world || player == null) return;
        SONAR.upload(level);
        PIPELINE.upload((mesh, key) -> {
            if (required(level, key, player.position(), client.options.getEffectiveRenderDistance()) != mesh.modes()) {
                mesh.close();
                return;
            }
            remove(key);
            MESHES.put(key, mesh);
        });
    }

    static void prepareDraw(RenderLevelStageEvent event, Vec3 player, boolean thermal, boolean sonar) {
        DRAW_MESHES.clear();
        DRAW_MODES.clear();
        if (sonar) SONAR.collectVisible(event, player, DRAW_MESHES, DRAW_MODES);
        if (!thermal || MESHES.isEmpty()) return;
        int viewBlocks = (Minecraft.getInstance().options.getEffectiveRenderDistance() + 1) * 16;
        for (var mesh : MESHES.values()) {
            long origin = mesh.origin();
            if (mesh.thermal().isEmpty() || Math.abs(BlockPos.getX(origin) - player.x) > viewBlocks || Math.abs(BlockPos.getZ(origin) - player.z) > viewBlocks) continue;
            if (!event.getFrustum().isVisible(mesh.bounds())) continue;
            DRAW_MESHES.add(mesh);
            DRAW_MODES.add((byte) VisionSectionMesh.THERMAL);
        }
    }

    static void updateSonarScan(Vec3 origin, double radiusSquared, boolean started) {
        if ((modes & VisionSectionMesh.SONAR) != 0) SONAR.update(origin, radiusSquared, started);
    }

    /** Cancels all stages before queuing a new revision, including updates received before a GPU mesh exists. */
    public static void invalidate(ClientLevel level, BlockPos position) {
        if (world != level) return;
        SHAPES.clear();
        if ((modes & VisionSectionMesh.THERMAL) == 0) return;
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
        if (event.getLevel() == world) {
            SONAR.unload(event.getChunk().getPos());
            chunkChanged(event.getChunk().getPos());
        }
    }

    private static void chunkChanged(ChunkPos chunk) {
        ClientLevel level = world;
        if (level == null || (modes & VisionSectionMesh.THERMAL) == 0) return;
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
        var sectionOffset = shader.safeGetUniform("SectionOffset");
        try {
            int mode = thermal ? VisionSectionMesh.THERMAL : VisionSectionMesh.SONAR;
            for (int index = 0; index < DRAW_MESHES.size(); index++) {
                if ((DRAW_MODES.getByte(index) & mode) == 0) continue;
                var mesh = DRAW_MESHES.get(index);
                var buffers = thermal ? mesh.thermal() : mesh.sonar();
                int x = BlockPos.getX(mesh.origin()), y = BlockPos.getY(mesh.origin()), z = BlockPos.getZ(mesh.origin());
                sectionOffset.set((float) (x - player.x), (float) (y - player.y), (float) (z - player.z));
                var view = DRAW_MATRIX.set(event.getModelViewMatrix()).translate((float) (x - camera.x), (float) (y - camera.y), (float) (z - camera.z));
                if (!thermal) draw(mesh.sonarSurface(), VertexFormat.Mode.QUADS, view, event.getProjectionMatrix(), shader);
                draw(buffers, thermal ? VertexFormat.Mode.QUADS : VertexFormat.Mode.DEBUG_LINES, view, event.getProjectionMatrix(), shader);
            }
        } finally {
            VertexBuffer.unbind();
        }
    }

    private static void draw(ObjectList<VertexBuffer> buffers, VertexFormat.Mode mode, Matrix4f view, Matrix4f projection, ShaderInstance shader) {
        if (buffers.isEmpty()) return;
        shader.setDefaultUniforms(mode, view, projection, Minecraft.getInstance().getWindow());
        shader.apply();
        try {
            for (var buffer : buffers) {
                buffer.bind();
                buffer.draw();
            }
        } finally {
            shader.clear();
        }
    }

    private static void remove(long key) {
        if (MESHES.containsKey(key)) MESHES.remove(key).close();
    }

    private static long origin(long section) {
        return BlockPos.asLong(SectionPos.sectionToBlockCoord(SectionPos.x(section)), SectionPos.sectionToBlockCoord(SectionPos.y(section)), SectionPos.sectionToBlockCoord(SectionPos.z(section)));
    }

    public static void clear() {
        PIPELINE.clear();
        SONAR.clear();
        SHAPES.clear();
        MESHES.values().forEach(VisionSectionMesh::close);
        MESHES.clear();
        PENDING.clear();
        BACKGROUND.clear();
        FAILED_CAPTURES.clear();
        DRAW_MESHES.clear();
        DRAW_MODES.clear();
        capture = null;
        survey = null;
        world = null;
        modes = 0;
        centerSection = Long.MIN_VALUE;
        surveyTicks = 0;
        prune = new long[0];
    }
}
