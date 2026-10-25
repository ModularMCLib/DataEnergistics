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

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;

import java.util.function.Consumer;

/** Owns GPU resources only on the render thread, releasing them on thaw, unload and resource reload. */
public final class DomainRenderCache {

    private record ObjectFrame(StasisDomain domain, FrozenGeometry geometry) {}

    private record DomainFrame(FrozenMaterialFrames materials, float shaderTime) {}

    private static final Reference2ObjectOpenHashMap<Entity, ObjectFrame> ENTITIES = new Reference2ObjectOpenHashMap<>();
    private static final Reference2ObjectOpenHashMap<Entity, ObjectFrame> FLAMES = new Reference2ObjectOpenHashMap<>();
    private static final Reference2ObjectOpenHashMap<BlockEntity, ObjectFrame> BLOCK_ENTITIES = new Reference2ObjectOpenHashMap<>();
    private static final Object2ObjectOpenHashMap<StasisDomain, DomainFrame> DOMAINS = new Object2ObjectOpenHashMap<>();

    private DomainRenderCache() {}

    public static void entity(Entity entity, PoseStack poses, Consumer<MultiBufferSource> renderer) {
        var domain = DomainClientState.covering(entity.level(), entity.chunkPosition().toLong(), entity);
        if (domain == null) throw new IllegalStateException("Unfrozen entity submitted to domain cache");
        var frame = ENTITIES.get(entity);
        if (frame == null || !frame.domain.equals(domain)) {
            if (frame != null) frame.geometry.close();
            frame = new ObjectFrame(domain, new FrozenGeometry(poses, materials(domain).materials, renderer));
            ENTITIES.put(entity, frame);
        }
        frame.geometry.draw(poses);
    }

    public static void blockEntity(BlockEntity entity, PoseStack poses, Consumer<MultiBufferSource> renderer) {
        var domain = DomainClientState.covering(entity.getLevel(), ChunkPos.asLong(entity.getBlockPos()), null);
        if (domain == null) throw new IllegalStateException("Unfrozen block entity submitted to domain cache");
        var frame = BLOCK_ENTITIES.get(entity);
        if (frame == null || !frame.domain.equals(domain)) {
            if (frame != null) frame.geometry.close();
            frame = new ObjectFrame(domain, new FrozenGeometry(poses, materials(domain).materials, renderer));
            BLOCK_ENTITIES.put(entity, frame);
        }
        frame.geometry.draw(poses);
    }

    public static void flame(Entity entity, PoseStack poses, Consumer<MultiBufferSource> renderer) {
        var domain = DomainClientState.covering(entity.level(), entity.chunkPosition().toLong(), entity);
        if (domain == null) throw new IllegalStateException("Unfrozen flame submitted to domain cache");
        var frame = FLAMES.get(entity);
        if (frame == null || !frame.domain.equals(domain)) {
            if (frame != null) frame.geometry.close();
            frame = new ObjectFrame(domain, new FrozenGeometry(poses, materials(domain).materials, renderer));
            FLAMES.put(entity, frame);
        }
        frame.geometry.draw(poses);
    }

    public static void chunk(StasisDomain domain, ShaderInstance shader, Runnable draw) {
        var frame = materials(domain);
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

    private static DomainFrame materials(StasisDomain domain) {
        return DOMAINS.computeIfAbsent(domain, key -> new DomainFrame(new FrozenMaterialFrames(), RenderSystem.getShaderGameTime()));
    }

    public static void retain() {
        FLAMES.reference2ObjectEntrySet().removeIf(entry -> {
            if (!entry.getKey().isRemoved() && entry.getKey().level() == Minecraft.getInstance().level && DomainStasis.frozen(entry.getKey())) return false;
            entry.getValue().geometry.close();
            return true;
        });
        ENTITIES.reference2ObjectEntrySet().removeIf(entry -> {
            if (!entry.getKey().isRemoved() && entry.getKey().level() == Minecraft.getInstance().level && DomainStasis.frozen(entry.getKey())) return false;
            entry.getValue().geometry.close();
            return true;
        });
        BLOCK_ENTITIES.reference2ObjectEntrySet().removeIf(entry -> {
            var entity = entry.getKey();
            if (!entity.isRemoved() && entity.getLevel() != null && entity.getLevel() == Minecraft.getInstance().level && DomainStasis.frozen(entity.getLevel(), entity.getBlockPos())) return false;
            entry.getValue().geometry.close();
            return true;
        });
        DOMAINS.object2ObjectEntrySet().removeIf(entry -> {
            if (DomainClientState.domains.contains(entry.getKey())) return false;
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
        DOMAINS.clear();
    }
}
