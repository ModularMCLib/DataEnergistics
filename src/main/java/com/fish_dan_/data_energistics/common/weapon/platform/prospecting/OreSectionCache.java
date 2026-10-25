package com.fish_dan_.data_energistics.common.weapon.platform.prospecting;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayFIFOQueue;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import org.jspecify.annotations.Nullable;

/** Shared across wearers. Cached sections expire every five seconds or immediately after a block mutation. */
public final class OreSectionCache {

    private static final Reference2ObjectOpenHashMap<ServerLevel, Object2ObjectOpenHashMap<ProspectingFilters, WorldCache>> WORLDS = new Reference2ObjectOpenHashMap<>();
    private static final ObjectArrayFIFOQueue<SectionRequest> PENDING = new ObjectArrayFIFOQueue<>();

    public static @Nullable OreSection scan(ServerLevel level, long section, ProspectingFilters filters) {
        long now = level.getServer().overworld().getGameTime();
        var world = WORLDS.computeIfAbsent(level, ignored -> new Object2ObjectOpenHashMap<>());
        var cache = world.computeIfAbsent(filters, ignored -> new WorldCache(level, filters));
        cache.lastUsed = now;
        Entry entry = cache.sections.get(section);
        if (entry != null && now - entry.at() < 100) return entry.value();
        if (cache.requested.add(section)) PENDING.enqueue(new SectionRequest(level, cache, section));
        return null;
    }

    /** FIFO requests share both a cheap palette budget and a separate budget for full ore sections. */
    @SubscribeEvent
    public void tick(ServerTickEvent.Post event) {
        long now = event.getServer().overworld().getGameTime();
        if (now % 100 == 0) {
            for (var world : WORLDS.values()) {
                var iterator = world.values().iterator();
                while (iterator.hasNext()) {
                    var cache = iterator.next();
                    if (now - cache.lastUsed > 600) {
                        cache.active = false;
                        iterator.remove();
                    }
                }
            }
        }
        int scanned = 0;
        for (int checked = 0; checked < 1024 && scanned < 64 && !PENDING.isEmpty(); checked++) {
            var request = PENDING.dequeue();
            ServerLevel level = request.level();
            var cache = request.cache();
            if (!cache.active || !cache.requested.remove(request.section())) continue;
            try {
                var pos = SectionPos.of(request.section());
                var chunk = level.getChunkSource().getChunk(pos.x(), pos.z(), ChunkStatus.FULL, false);
                if (chunk == null) {
                    cache.sections.remove(request.section());
                    continue;
                }
                var data = chunk.getSection(level.getSectionIndexFromSectionY(pos.y()));
                var positions = new IntArrayList();
                var states = new IntArrayList();
                if (data.maybeHas(state -> cache.blocks.contains(state.getBlock()))) {
                    scanned++;
                    for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                        var state = data.getBlockState(x, y, z);
                        if (cache.blocks.contains(state.getBlock())) {
                            positions.add(x | z << 4 | y << 8);
                            states.add(Block.getId(state));
                        }
                    }
                }
                cache.sections.put(request.section(), new Entry(now, new OreSection(request.section(), positions, states)));
            } catch (RuntimeException failure) {
                Data_Energistics.LOGGER.error("Ore section scan failed at {} in {}", SectionPos.of(request.section()), level.dimension().location(), failure);
            }
        }
    }

    public static void changed(Level level, BlockPos pos) {
        var world = WORLDS.get(level);
        if (world != null) for (var cache : world.values()) cache.sections.remove(SectionPos.of(pos).asLong());
    }

    @SubscribeEvent
    public void tagsUpdated(TagsUpdatedEvent event) {
        if (event.getUpdateCause() != TagsUpdatedEvent.UpdateCause.SERVER_DATA_LOAD) return;
        WORLDS.clear();
        PENDING.clear();
    }

    @SubscribeEvent
    public void stopped(ServerStoppedEvent event) {
        WORLDS.clear();
        PENDING.clear();
    }

    @SubscribeEvent
    public void unload(ChunkEvent.Unload event) {
        var world = WORLDS.get(event.getLevel());
        if (world == null) return;
        var pos = event.getChunk().getPos();
        for (var cache : world.values()) {
            cache.sections.keySet().removeIf(key -> SectionPos.x(key) == pos.x && SectionPos.z(key) == pos.z);
            cache.requested.removeIf(key -> SectionPos.x(key) == pos.x && SectionPos.z(key) == pos.z);
        }
    }

    private static final class WorldCache {

        private final Long2ObjectOpenHashMap<Entry> sections = new Long2ObjectOpenHashMap<>();
        private final LongOpenHashSet requested = new LongOpenHashSet();
        private final ReferenceOpenHashSet<Block> blocks = new ReferenceOpenHashSet<>();
        private long lastUsed;
        private boolean active = true;

        private WorldCache(ServerLevel level, ProspectingFilters filters) {
            var registry = level.registryAccess().registryOrThrow(Registries.BLOCK);
            for (var entry : filters.entries()) {
                entry.block().flatMap(registry::getOptional).ifPresent(blocks::add);
                entry.tag().flatMap(id -> registry.getTag(TagKey.create(Registries.BLOCK, id)))
                        .ifPresent(holders -> holders.forEach(holder -> blocks.add(holder.value())));
            }
        }
    }

    private record SectionRequest(ServerLevel level, WorldCache cache, long section) {}

    private record Entry(long at, OreSection value) {}
}
