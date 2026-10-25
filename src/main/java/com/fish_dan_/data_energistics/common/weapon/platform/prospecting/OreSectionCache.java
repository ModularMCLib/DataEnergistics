package com.fish_dan_.data_energistics.common.weapon.platform.prospecting;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayFIFOQueue;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import org.jspecify.annotations.Nullable;

/** Shared across wearers. Cached sections expire every five seconds or immediately after a block mutation. */
public final class OreSectionCache {

    private static final Reference2ObjectOpenHashMap<ServerLevel, WorldCache> WORLDS = new Reference2ObjectOpenHashMap<>();
    private static final ObjectArrayFIFOQueue<SectionRequest> PENDING = new ObjectArrayFIFOQueue<>();

    public static @Nullable OreSection scan(ServerLevel level, long section) {
        long now = level.getServer().overworld().getGameTime();
        var cache = WORLDS.computeIfAbsent(level, ignored -> new WorldCache(new Long2ObjectOpenHashMap<>(), new LongOpenHashSet()));
        Entry entry = cache.sections().get(section);
        if (entry != null && now - entry.at() < 100) return entry.value();
        if (cache.requested().add(section)) PENDING.enqueue(new SectionRequest(level, section));
        return null;
    }

    /** FIFO requests share both a cheap palette budget and a separate budget for full ore sections. */
    @SubscribeEvent
    public void tick(ServerTickEvent.Post event) {
        long now = event.getServer().overworld().getGameTime();
        int scanned = 0;
        for (int checked = 0; checked < 1024 && scanned < 64 && !PENDING.isEmpty(); checked++) {
            var request = PENDING.dequeue();
            ServerLevel level = request.level();
            var cache = WORLDS.get(level);
            if (cache == null || !cache.requested().remove(request.section())) continue;
            try {
                var pos = SectionPos.of(request.section());
                var chunk = level.getChunkSource().getChunk(pos.x(), pos.z(), ChunkStatus.FULL, false);
                if (chunk == null) {
                    cache.sections().remove(request.section());
                    continue;
                }
                var data = chunk.getSection(level.getSectionIndexFromSectionY(pos.y()));
                var positions = new IntArrayList();
                var states = new IntArrayList();
                if (data.maybeHas(state -> state.is(Tags.Blocks.ORES))) {
                    scanned++;
                    for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                        var state = data.getBlockState(x, y, z);
                        if (state.is(Tags.Blocks.ORES)) {
                            positions.add(x | z << 4 | y << 8);
                            states.add(Block.getId(state));
                        }
                    }
                }
                cache.sections().put(request.section(), new Entry(now, new OreSection(request.section(), positions, states)));
            } catch (RuntimeException failure) {
                Data_Energistics.LOGGER.error("Ore section scan failed at {} in {}", SectionPos.of(request.section()), level.dimension().location(), failure);
            }
        }
    }

    public static void changed(Level level, BlockPos pos) {
        var cache = WORLDS.get(level);
        if (cache != null) cache.sections().remove(SectionPos.of(pos).asLong());
    }

    @SubscribeEvent
    public void stopped(ServerStoppedEvent event) {
        WORLDS.clear();
        PENDING.clear();
    }

    @SubscribeEvent
    public void unload(ChunkEvent.Unload event) {
        var cache = WORLDS.get(event.getLevel());
        if (cache == null) return;
        var pos = event.getChunk().getPos();
        cache.sections().keySet().removeIf(key -> SectionPos.x(key) == pos.x && SectionPos.z(key) == pos.z);
        cache.requested().removeIf(key -> SectionPos.x(key) == pos.x && SectionPos.z(key) == pos.z);
    }

    private record WorldCache(Long2ObjectOpenHashMap<Entry> sections, LongOpenHashSet requested) {}

    private record SectionRequest(ServerLevel level, long section) {}

    private record Entry(long at, OreSection value) {}
}
