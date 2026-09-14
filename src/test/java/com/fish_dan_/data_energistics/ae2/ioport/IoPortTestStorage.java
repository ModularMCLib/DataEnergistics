package com.fish_dan_.data_energistics.ae2.ioport;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;

import net.minecraft.network.chat.Component;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenHashMap;
import org.jspecify.annotations.Nullable;

/** Controllable third-party storage boundary; production transfer code is exercised without reflection. */
class IoPortTestStorage implements MEStorage {

    final Object2LongLinkedOpenHashMap<AEKey> contents = new Object2LongLinkedOpenHashMap<>();
    final LongArrayList insertions = new LongArrayList();
    long capacity = Long.MAX_VALUE;
    long actualInsertLimit = Long.MAX_VALUE;
    boolean rejectInsert;
    boolean voiding;
    @Nullable
    AEKey acceptedKey;

    @Override
    public long insert(AEKey key, long amount, Actionable mode, IActionSource source) {
        if (rejectInsert || acceptedKey != null && !acceptedKey.equals(key)) return 0;
        long inserted = Math.min(amount, capacity - contents.getLong(key));
        if (mode == Actionable.MODULATE) {
            inserted = Math.min(inserted, actualInsertLimit);
            if (inserted > 0) {
                insertions.add(inserted);
                if (!voiding) contents.addTo(key, inserted);
            }
        }
        return inserted;
    }

    @Override
    public long extract(AEKey key, long amount, Actionable mode, IActionSource source) {
        long extracted = Math.min(amount, contents.getLong(key));
        if (mode == Actionable.MODULATE && extracted > 0) {
            long remaining = contents.getLong(key) - extracted;
            if (remaining == 0) contents.removeLong(key);
            else contents.put(key, remaining);
        }
        return extracted;
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        for (var entry : contents.object2LongEntrySet()) out.add(entry.getKey(), entry.getLongValue());
    }

    @Override
    public Component getDescription() {
        return Component.literal("IO port test storage");
    }
}
