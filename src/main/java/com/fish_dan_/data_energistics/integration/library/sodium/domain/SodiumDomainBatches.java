package com.fish_dan_.data_energistics.integration.library.sodium.domain;

import com.fish_dan_.data_energistics.client.render.domain.DomainRenderCache;
import com.fish_dan_.data_energistics.client.weapon.platform.DomainClientState;
import com.fish_dan_.data_energistics.common.weapon.platform.domain.StasisDomain;

import net.caffeinemc.mods.sodium.client.gl.device.MultiDrawBatch;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.ChunkPos;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.Pointer;

import java.util.Objects;
import java.util.function.Consumer;

/** Render-thread contiguous spans preserve Sodium's terrain and translucent sorting order. */
public final class SodiumDomainBatches {

    private static final Reference2ObjectOpenHashMap<MultiDrawBatch, Batch> BATCHES = new Reference2ObjectOpenHashMap<>();

    private SodiumDomainBatches() {}

    public static void begin(MultiDrawBatch batch) {
        var state = BATCHES.get(batch);
        if (state != null) state.spans.clear();
    }

    public static void section(MultiDrawBatch batch, int x, int z) {
        long chunk = ChunkPos.asLong(x, z);
        var domain = DomainClientState.covering(Minecraft.getInstance().level, chunk, null);
        if (domain != null) domain = DomainRenderCache.retainChunk(chunk, domain);
        var state = BATCHES.get(batch);
        if (state == null) {
            if (domain == null) return;
            state = new Batch(new MultiDrawBatch(batch.capacity()), new ObjectArrayList<>());
            BATCHES.put(batch, state);
        }
        var spans = state.spans;
        if (spans.isEmpty() && batch.size > 0) spans.add(new Span(0, 0, null));
        if (!spans.isEmpty() && spans.getLast().start == batch.size) spans.removeLast();
        if (spans.isEmpty() || !Objects.equals(spans.getLast().domain, domain)) spans.add(new Span(batch.size, chunk, domain));
    }

    public static void draw(MultiDrawBatch batch, Consumer<MultiDrawBatch> draw) {
        var state = BATCHES.get(batch);
        if (state == null || state.spans.stream().noneMatch(span -> span.domain != null)) {
            draw.accept(batch);
            return;
        }
        for (int index = 0; index < state.spans.size(); index++) {
            var span = state.spans.get(index);
            int end = index + 1 == state.spans.size() ? batch.size : state.spans.get(index + 1).start;
            int count = end - span.start;
            if (count == 0) continue;
            state.scratch.size = count;
            MemoryUtil.memCopy(batch.pElementPointer + (long) span.start * Pointer.POINTER_SIZE, state.scratch.pElementPointer, (long) count * Pointer.POINTER_SIZE);
            MemoryUtil.memCopy(batch.pElementCount + (long) span.start * Integer.BYTES, state.scratch.pElementCount, (long) count * Integer.BYTES);
            MemoryUtil.memCopy(batch.pBaseVertex + (long) span.start * Integer.BYTES, state.scratch.pBaseVertex, (long) count * Integer.BYTES);
            if (span.domain == null) draw.accept(state.scratch);
            else DomainRenderCache.chunk(span.chunk, span.domain, () -> draw.accept(state.scratch));
        }
    }

    public static void delete(MultiDrawBatch batch) {
        var state = BATCHES.remove(batch);
        if (state != null) state.scratch.delete();
    }

    private record Batch(MultiDrawBatch scratch, ObjectArrayList<Span> spans) {}

    private record Span(int start, long chunk, @Nullable StasisDomain domain) {}
}
