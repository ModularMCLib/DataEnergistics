package com.fish_dan_.data_energistics.common.weapon.platform.prospecting;

import com.fish_dan_.data_energistics.common.data.DEDataComponents;
import com.fish_dan_.data_energistics.common.weapon.platform.model.PlatformAbility;
import com.fish_dan_.data_energistics.common.weapon.platform.network.PlatformRpc;
import com.fish_dan_.data_energistics.common.weapon.platform.runtime.PlatformRuntime;
import com.fish_dan_.data_energistics.common.weapon.platform.storage.PlatformAbilitySettings;

import net.minecraft.core.SectionPos;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

/** A five-second sweep is incremental and shares section scans with every nearby platform. */
public final class PlatformOreSurvey {

    private final Long2ObjectOpenHashMap<OreSection> sent = new Long2ObjectOpenHashMap<>();
    private final LongLinkedOpenHashSet pending = new LongLinkedOpenHashSet();
    private final LongArrayList removed = new LongArrayList();
    private long nextRefresh;
    private boolean running;
    private boolean reset;

    public void tick(PlatformRuntime runtime) {
        var player = runtime.player;
        if ((runtime.abilities.active & PlatformAbility.PROSPECTING.flag) == 0) {
            if (running) PlatformRpc.ores(player, new OreSurveyDelta(true, new ObjectArrayList<>(), new LongArrayList()));
            running = false;
            pending.clear();
            sent.clear();
            removed.clear();
            return;
        }
        long now = player.serverLevel().getServer().overworld().getGameTime();
        if (!running || now >= nextRefresh) {
            reset = !running;
            running = true;
            nextRefresh = now + 100;
            int radius = runtime.platform.getOrDefault(DEDataComponents.PLATFORM_ABILITIES, PlatformAbilitySettings.DEFAULT).scanRadius();
            int cx = player.chunkPosition().x, cz = player.chunkPosition().z, chunks = (radius + 15) / 16;
            var included = new LongOpenHashSet();
            for (int ring = 0; ring <= chunks; ring++) for (int dx = -ring; dx <= ring; dx++) for (int dz = -ring; dz <= ring; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) != ring || dx * dx + dz * dz > (chunks + 1) * (chunks + 1) || !player.serverLevel().getChunkSource().hasChunk(cx + dx, cz + dz)) continue;
                for (int y = player.level().getMinSection(); y < player.level().getMaxSection(); y++) {
                    long key = SectionPos.asLong(cx + dx, y, cz + dz);
                    pending.add(key);
                    included.add(key);
                }
            }
            pending.removeIf(key -> !included.contains(key));
            var iterator = sent.keySet().iterator();
            while (iterator.hasNext()) {
                long key = iterator.nextLong();
                if (!included.contains(key)) {
                    removed.add(key);
                    iterator.remove();
                }
            }
        }
        var batch = new ObjectArrayList<OreSection>();
        int attempts = Math.min(pending.size(), 512);
        for (int count = 0; count < attempts && batch.size() < 16; count++) {
            long key = pending.removeFirstLong();
            OreSection value = OreSectionCache.scan(player.serverLevel(), key);
            if (value == null) pending.add(key);
            else if (value.positions().isEmpty()) {
                if (sent.remove(key) != null) removed.add(key);
            } else if (!value.equals(sent.put(key, value))) batch.add(value);
        }
        if (reset || !batch.isEmpty() || !removed.isEmpty()) PlatformRpc.ores(player, new OreSurveyDelta(reset, batch, new LongArrayList(removed)));
        reset = false;
        removed.clear();
    }
}
