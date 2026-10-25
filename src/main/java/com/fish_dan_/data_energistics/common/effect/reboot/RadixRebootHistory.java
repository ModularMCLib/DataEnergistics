package com.fish_dan_.data_energistics.common.effect.reboot;

import net.minecraft.server.level.ServerPlayer;

import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import org.jspecify.annotations.Nullable;

/** Persisted one-second FIFO samples; normal death starts a new life without the previous history. */
public final class RadixRebootHistory {

    private static final int SAMPLES = 21;
    public static final Codec<RadixRebootHistory> CODEC = RadixRebootSnapshot.CODEC.listOf(0, SAMPLES).xmap(values -> {
        var history = new RadixRebootHistory();
        history.frames.addAll(values);
        history.cursor = values.size() % SAMPLES;
        return history;
    }, RadixRebootHistory::snapshots);
    private final ObjectArrayList<RadixRebootSnapshot> frames = new ObjectArrayList<>(SAMPLES);
    private int cursor;
    private long lastTick = Long.MIN_VALUE;
    boolean expired;
    boolean restoring;

    public void record(ServerPlayer player, long tick) {
        if (lastTick != Long.MIN_VALUE && tick - lastTick < 20) return;
        lastTick = tick;
        var snapshot = RadixRebootSnapshot.capture(player);
        if (frames.size() < SAMPLES) frames.add(snapshot);
        else frames.set(cursor, snapshot);
        cursor = (cursor + 1) % SAMPLES;
    }

    public @Nullable RadixRebootSnapshot rewind() {
        return frames.isEmpty() ? null : frames.get(frames.size() == SAMPLES ? cursor : 0);
    }

    public void clear() {
        frames.clear();
        cursor = 0;
        lastTick = Long.MIN_VALUE;
        expired = false;
    }

    private ObjectList<RadixRebootSnapshot> snapshots() {
        var ordered = new ObjectArrayList<RadixRebootSnapshot>(frames.size());
        int start = frames.size() == SAMPLES ? cursor : 0;
        for (int index = 0; index < frames.size(); index++) ordered.add(frames.get((index + start) % frames.size()));
        return ordered;
    }
}
