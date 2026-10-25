package com.fish_dan_.data_energistics.client.render.domain;

import com.fish_dan_.data_energistics.client.weapon.platform.DomainClientState;
import com.fish_dan_.data_energistics.common.weapon.platform.domain.DomainStasis;
import com.fish_dan_.data_energistics.common.weapon.platform.domain.StasisDomain;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

import java.util.function.Consumer;

/** Owns GPU resources only on the render thread, releasing them on thaw, unload and resource reload. */
public final class DomainRenderCache {

    private record ObjectFrame(StasisDomain domain, FrozenGeometry geometry) {}

    private record DomainFrame(FrozenMaterialFrames materials, float shaderTime, FrozenProgramClock programClock) {}

    private static final Reference2ObjectOpenHashMap<Entity, ObjectFrame> ENTITIES = new Reference2ObjectOpenHashMap<>();
    private static final Reference2ObjectOpenHashMap<Entity, ObjectFrame> FLAMES = new Reference2ObjectOpenHashMap<>();
    private static final Reference2ObjectOpenHashMap<BlockEntity, ObjectFrame> BLOCK_ENTITIES = new Reference2ObjectOpenHashMap<>();
    private static final Object2ObjectOpenHashMap<StasisDomain, DomainFrame> DOMAINS = new Object2ObjectOpenHashMap<>();
    private static final Long2ObjectOpenHashMap<StasisDomain> CHUNKS = new Long2ObjectOpenHashMap<>();

    private DomainRenderCache() {}

    public static void entity(Entity entity, PoseStack poses, Consumer<MultiBufferSource> renderer) {
        var domain = DomainClientState.covering(entity.level(), entity.chunkPosition().toLong(), entity);
        if (domain == null) throw new IllegalStateException("Unfrozen entity submitted to domain cache");
        var frame = ENTITIES.get(entity);
        if (frame == null) {
            frame = new ObjectFrame(domain, new FrozenGeometry(poses, materials(domain).materials, renderer));
            ENTITIES.put(entity, frame);
        }
        frame.geometry.draw(poses);
    }

    public static void blockEntity(BlockEntity entity, PoseStack poses, Consumer<MultiBufferSource> renderer) {
        var domain = DomainClientState.covering(entity.getLevel(), ChunkPos.asLong(entity.getBlockPos()), null);
        if (domain == null) throw new IllegalStateException("Unfrozen block entity submitted to domain cache");
        var frame = BLOCK_ENTITIES.get(entity);
        if (frame == null) {
            frame = new ObjectFrame(domain, new FrozenGeometry(poses, materials(domain).materials, renderer));
            BLOCK_ENTITIES.put(entity, frame);
        }
        frame.geometry.draw(poses);
    }

    public static void flame(Entity entity, PoseStack poses, Consumer<MultiBufferSource> renderer) {
        var domain = DomainClientState.covering(entity.level(), entity.chunkPosition().toLong(), entity);
        if (domain == null) throw new IllegalStateException("Unfrozen flame submitted to domain cache");
        var frame = FLAMES.get(entity);
        if (frame == null) {
            frame = new ObjectFrame(domain, new FrozenGeometry(poses, materials(domain).materials, renderer));
            FLAMES.put(entity, frame);
        }
        frame.geometry.draw(poses);
    }

    public static void chunk(long chunk, StasisDomain domain, ShaderInstance shader, Runnable draw) {
        var frame = materials(retainChunk(chunk, domain));
        int live = RenderSystem.getShaderTexture(0);
        shader.setSampler("Sampler0", frame.materials.texture(live));
        if (shader.GAME_TIME != null) shader.GAME_TIME.set(frame.shaderTime);
        shader.apply();
        try {
            draw.run();
        } finally {
            shader.setSampler("Sampler0", live);
            if (shader.GAME_TIME != null) shader.GAME_TIME.set(RenderSystem.getShaderGameTime());
            shader.apply();
        }
    }

    /** Sodium and Iris keep their active program and sampler layout while the affected span uses a local clock. */
    public static void chunk(long chunk, StasisDomain domain, Runnable draw) {
        var frame = materials(retainChunk(chunk, domain));
        int activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
        int live = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try {
            GlStateManager._bindTexture(frame.materials.texture(live));
            frame.programClock.draw(draw);
        } finally {
            GlStateManager._bindTexture(live);
            GlStateManager._activeTexture(activeTexture);
        }
    }

    /** Registers every section's chunk before native backends coalesce it with adjacent draw commands. */
    public static StasisDomain retainChunk(long chunk, StasisDomain domain) {
        return CHUNKS.computeIfAbsent(chunk, ignored -> domain);
    }

    private static DomainFrame materials(StasisDomain domain) {
        return DOMAINS.computeIfAbsent(domain, key -> new DomainFrame(new FrozenMaterialFrames(), RenderSystem.getShaderGameTime(), new FrozenProgramClock()));
    }

    public static void retain() {
        var level = Minecraft.getInstance().level;
        FLAMES.reference2ObjectEntrySet().removeIf(entry -> {
            if (!entry.getKey().isRemoved() && entry.getKey().level() == level && DomainStasis.frozen(entry.getKey())) return false;
            entry.getValue().geometry.close();
            return true;
        });
        ENTITIES.reference2ObjectEntrySet().removeIf(entry -> {
            if (!entry.getKey().isRemoved() && entry.getKey().level() == level && DomainStasis.frozen(entry.getKey())) return false;
            entry.getValue().geometry.close();
            return true;
        });
        BLOCK_ENTITIES.reference2ObjectEntrySet().removeIf(entry -> {
            var entity = entry.getKey();
            if (!entity.isRemoved() && entity.getLevel() != null && entity.getLevel() == level && DomainStasis.frozen(entity.getLevel(), entity.getBlockPos())) return false;
            entry.getValue().geometry.close();
            return true;
        });
        CHUNKS.long2ObjectEntrySet().removeIf(entry -> level == null || entry.getValue().dimension() != level.dimension() || !DomainClientState.frozen(level, entry.getLongKey()) || !level.hasChunk(ChunkPos.getX(entry.getLongKey()), ChunkPos.getZ(entry.getLongKey())));
        // Overlapping domains keep the first captured phase until the object or chunk really thaws.
        var retained = new ObjectOpenHashSet<>(DomainClientState.domains);
        retained.addAll(CHUNKS.values());
        ENTITIES.values().forEach(frame -> retained.add(frame.domain));
        FLAMES.values().forEach(frame -> retained.add(frame.domain));
        BLOCK_ENTITIES.values().forEach(frame -> retained.add(frame.domain));
        DOMAINS.object2ObjectEntrySet().removeIf(entry -> {
            if (retained.contains(entry.getKey())) return false;
            entry.getValue().materials.close();
            return true;
        });
    }

    public static void clear() {
        if (!RenderSystem.isOnRenderThread()) {
            Minecraft.getInstance().execute(DomainRenderCache::clear);
            return;
        }
        ENTITIES.values().forEach(frame -> frame.geometry.close());
        FLAMES.values().forEach(frame -> frame.geometry.close());
        BLOCK_ENTITIES.values().forEach(frame -> frame.geometry.close());
        DOMAINS.values().forEach(frame -> frame.materials.close());
        ENTITIES.clear();
        FLAMES.clear();
        BLOCK_ENTITIES.clear();
        CHUNKS.clear();
        DOMAINS.clear();
    }
}
