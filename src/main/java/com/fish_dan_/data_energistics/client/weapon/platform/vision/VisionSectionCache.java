package com.fish_dan_.data_energistics.client.weapon.platform.vision;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.client.registry.DEShaders;
import com.fish_dan_.data_energistics.common.weapon.platform.model.PlatformAbility;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
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
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

/** Loaded sections only; expensive shape reads run in bounded client-tick batches, never in the draw loop. */
@EventBusSubscriber(modid = Data_Energistics.MODID, value = Dist.CLIENT)
public final class VisionSectionCache {

    public static final int SONAR_RADIUS = 48;
    public static final int SONAR_RADIUS_SQUARED = SONAR_RADIUS * SONAR_RADIUS;
    private static final Long2ObjectOpenHashMap<VisionSectionMesh> MESHES = new Long2ObjectOpenHashMap<>();
    private static final LongLinkedOpenHashSet PENDING = new LongLinkedOpenHashSet();
    private static @Nullable ClientLevel world;
    private static int modes;
    private static int surveyTicks;
    private static long centerSection = Long.MIN_VALUE;

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
        long center = SectionPos.asLong(client.player.blockPosition());
        if (center != centerSection || surveyTicks-- <= 0) {
            centerSection = center;
            surveyTicks = 20;
            survey(client.level, client.player.position(), client.options.getEffectiveRenderDistance());
        }
        long started = System.nanoTime();
        for (int built = 0; !PENDING.isEmpty() && built < 4 && (built == 0 || System.nanoTime() - started < 3_000_000); built++) {
            long key = PENDING.removeFirstLong();
            var chunk = client.level.getChunkSource().getChunk(SectionPos.x(key), SectionPos.z(key), false);
            if (chunk == null || client.level.isOutsideBuildHeight(SectionPos.sectionToBlockCoord(SectionPos.y(key)))) {
                remove(key);
                continue;
            }
            LevelChunkSection section = chunk.getSection(client.level.getSectionIndexFromSectionY(SectionPos.y(key)));
            int required = required(client.level, section, SectionPos.of(key).origin(), client.player.position(), client.options.getEffectiveRenderDistance());
            remove(key);
            if (required != 0) MESHES.put(key, VisionSectionMesh.build(client.level, key, section, required));
        }
    }

    private static void survey(ClientLevel level, Vec3 player, int viewDistance) {
        var wanted = new LongOpenHashSet();
        int distance = (modes & VisionSectionMesh.THERMAL) != 0 ? viewDistance : 3;
        int cx = SectionPos.blockToSectionCoord(player.x), cz = SectionPos.blockToSectionCoord(player.z);
        for (int x = cx - distance; x <= cx + distance; x++) for (int z = cz - distance; z <= cz + distance; z++) {
            var chunk = level.getChunkSource().getChunk(x, z, false);
            if (chunk == null) continue;
            for (int index = 0; index < chunk.getSections().length; index++) {
                int y = level.getSectionYFromSectionIndex(index);
                long key = SectionPos.asLong(x, y, z);
                var section = chunk.getSection(index);
                int required = required(level, section, SectionPos.of(key).origin(), player, viewDistance);
                if (required == 0) continue;
                wanted.add(key);
                if (MESHES.containsKey(key) && MESHES.get(key).modes() == required && !section.maybeHas(BlockState::hasDynamicLightEmission)) continue;
                if ((required & VisionSectionMesh.SONAR) != 0) PENDING.addAndMoveToFirst(key);
                else PENDING.add(key);
            }
        }
        var iterator = MESHES.long2ObjectEntrySet().fastIterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (!wanted.contains(entry.getLongKey())) {
                entry.getValue().close();
                iterator.remove();
            }
        }
    }

    private static int required(ClientLevel level, LevelChunkSection section, BlockPos origin, Vec3 player, int viewDistance) {
        if (section.hasOnlyAir()) return 0;
        int result = 0;
        if ((modes & VisionSectionMesh.SONAR) != 0 && bounds(origin).distanceToSqr(player) <= SONAR_RADIUS_SQUARED) result |= VisionSectionMesh.SONAR;
        if ((modes & VisionSectionMesh.THERMAL) != 0 && Math.abs(origin.getX() - player.x) <= (viewDistance + 1) * 16 && Math.abs(origin.getZ() - player.z) <= (viewDistance + 1) * 16 && section.maybeHas(state -> ThermalSources.environmental(state, level, origin))) result |= VisionSectionMesh.THERMAL;
        return result;
    }

    /** Called after a client chunk has accepted a block update, including neighboring section faces at boundaries. */
    public static void invalidate(ClientLevel level, BlockPos position) {
        if (world != level) return;
        for (int x = (position.getX() - 1) >> 4; x <= (position.getX() + 1) >> 4; x++)
            for (int y = (position.getY() - 1) >> 4; y <= (position.getY() + 1) >> 4; y++)
                for (int z = (position.getZ() - 1) >> 4; z <= (position.getZ() + 1) >> 4; z++) PENDING.addAndMoveToFirst(SectionPos.asLong(x, y, z));
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
        surveyTicks = 0;
        for (long key : MESHES.keySet()) if (Math.abs(SectionPos.x(key) - chunk.x) <= 1 && Math.abs(SectionPos.z(key) - chunk.z) <= 1) PENDING.addAndMoveToFirst(key);
    }

    static void draw(RenderLevelStageEvent event, Vec3 player, boolean thermal) {
        var shader = DEShaders.getVisionSurface();
        var camera = event.getCamera().getPosition();
        shader.safeGetUniform("ScanRadiusSquared").set(thermal ? 0f : (float) SONAR_RADIUS_SQUARED);
        try {
            for (var mesh : MESHES.values()) {
                var buffer = thermal ? mesh.thermal() : mesh.sonar();
                var surface = thermal ? null : mesh.sonarSurface();
                if (buffer == null && surface == null || !event.getFrustum().isVisible(bounds(mesh.origin()))) continue;
                shader.safeGetUniform("SectionOffset").set((float) (mesh.origin().getX() - player.x), (float) (mesh.origin().getY() - player.y), (float) (mesh.origin().getZ() - player.z));
                var view = new Matrix4f(event.getModelViewMatrix()).translate((float) (mesh.origin().getX() - camera.x), (float) (mesh.origin().getY() - camera.y), (float) (mesh.origin().getZ() - camera.z));
                if (surface != null) {
                    surface.bind();
                    surface.drawWithShader(view, event.getProjectionMatrix(), shader);
                }
                if (buffer != null) {
                    buffer.bind();
                    buffer.drawWithShader(view, event.getProjectionMatrix(), shader);
                }
            }
        } finally {
            VertexBuffer.unbind();
        }
    }

    private static void remove(long key) {
        if (MESHES.containsKey(key)) MESHES.remove(key).close();
    }

    private static AABB bounds(BlockPos origin) {
        return new AABB(origin.getX(), origin.getY(), origin.getZ(), origin.getX() + 16, origin.getY() + 16, origin.getZ() + 16);
    }

    public static void clear() {
        MESHES.values().forEach(VisionSectionMesh::close);
        MESHES.clear();
        PENDING.clear();
        world = null;
        modes = 0;
        centerSection = Long.MIN_VALUE;
        surveyTicks = 0;
    }
}
