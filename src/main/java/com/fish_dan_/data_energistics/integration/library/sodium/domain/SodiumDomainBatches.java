package com.fish_dan_.data_energistics.integration.library.sodium.domain;

import com.fish_dan_.data_energistics.client.render.domain.DomainRenderCache;
import com.fish_dan_.data_energistics.client.weapon.platform.DomainClientState;
import com.fish_dan_.data_energistics.common.weapon.platform.domain.StasisDomain;

import net.caffeinemc.mods.sodium.client.gl.device.MultiDrawBatch;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.ChunkPos;

import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.Pointer;

import java.util.function.Consumer;

/** Render-thread contiguous spans preserve Sodium's terrain and translucent sorting order. */
public final class SodiumDomainBatches {

    private static final Reference2ObjectOpenHashMap<MultiDrawBatch, @Nullable Batch> BATCHES = new Reference2ObjectOpenHashMap<>();

    private SodiumDomainBatches() {}

    public static void begin(MultiDrawBatch batch) {
        var state = BATCHES.get(batch);
        if (state != null) state.ranges.clear();
    }

    public static void section(MultiDrawBatch batch, int x, int z) {
        var state = BATCHES.get(batch);
        if (state == null) {
            state = new Batch();
            BATCHES.put(batch, state);
        }
        state.ranges.section(batch.size, ChunkPos.asLong(x, z));
    }

    public static void draw(MultiDrawBatch batch, Consumer<MultiDrawBatch> draw) {
        var state = BATCHES.get(batch);
        if (state == null || DomainClientState.domains.isEmpty()) {
            draw.accept(batch);
            return;
        }
        var ranges = state.ranges.resolve(batch.size, SodiumDomainBatches::domain);
        if (ranges.stream().noneMatch(range -> range.domain() != null)) {
            draw.accept(batch);
            return;
        }
        var scratch = state.scratch(batch.size);
        for (var range : ranges) {
            scratch.size = range.count();
            MemoryUtil.memCopy(batch.pElementPointer + (long) range.start() * Pointer.POINTER_SIZE, scratch.pElementPointer, (long) range.count() * Pointer.POINTER_SIZE);
            MemoryUtil.memCopy(batch.pElementCount + (long) range.start() * Integer.BYTES, scratch.pElementCount, (long) range.count() * Integer.BYTES);
            MemoryUtil.memCopy(batch.pBaseVertex + (long) range.start() * Integer.BYTES, scratch.pBaseVertex, (long) range.count() * Integer.BYTES);
            if (range.domain() == null) draw.accept(scratch);
            else DomainRenderCache.chunk(range.chunk(), range.domain(), () -> draw.accept(scratch));
        }
    }

    private static @Nullable StasisDomain domain(long chunk) {
        var domain = DomainClientState.covering(Minecraft.getInstance().level, chunk, null);
        return domain == null ? null : DomainRenderCache.retainChunk(chunk, domain);
    }

    public static void delete(MultiDrawBatch batch) {
        var state = BATCHES.remove(batch);
        if (state != null && state.scratch != null) state.scratch.delete();
    }

    private static final class Batch {

        private final DomainDrawRanges ranges = new DomainDrawRanges();
        private @Nullable MultiDrawBatch scratch;
        private int capacity;

        private MultiDrawBatch scratch(int requiredCapacity) {
            var current = scratch;
            if (current != null && capacity >= requiredCapacity) return current;
            if (current != null) current.delete();
            current = new MultiDrawBatch(requiredCapacity);
            scratch = current;
            capacity = requiredCapacity;
            return current;
        }
    }
}
