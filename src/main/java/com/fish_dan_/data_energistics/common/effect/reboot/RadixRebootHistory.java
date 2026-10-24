package com.fish_dan_.data_energistics.common.effect.reboot;

import net.minecraft.server.level.ServerPlayer;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jspecify.annotations.Nullable;

/** Transient history belongs to one player object and never follows a normal death into a new life. */
public final class RadixRebootHistory {

    private static final int HISTORY_TICKS = 400;
    private final ObjectArrayList<Frame> frames = new ObjectArrayList<>(HISTORY_TICKS + 1);
    private int cursor;
    private long lastTick = Long.MIN_VALUE;
    boolean expired;
    boolean restoring;

    public void record(ServerPlayer player, long tick) {
        if (lastTick == tick) return;
        lastTick = tick;
        Frame frame = new Frame(tick, RadixRebootSnapshot.capture(player));
        if (frames.size() <= cursor) frames.add(frame);
        else frames.set(cursor, frame);
        cursor = (cursor + 1) % (HISTORY_TICKS + 1);
    }

    public @Nullable RadixRebootSnapshot rewind(long tick) {
        RadixRebootSnapshot snapshot = null;
        for (int offset = 1; offset <= frames.size(); offset++) {
            Frame frame = frames.get(Math.floorMod(cursor - offset, frames.size()));
            snapshot = frame.snapshot;
            if (frame.tick <= tick - HISTORY_TICKS) break;
        }
        return snapshot;
    }

    public void clear() {
        frames.clear();
        cursor = 0;
        lastTick = Long.MIN_VALUE;
        expired = false;
    }

    private record Frame(long tick, RadixRebootSnapshot snapshot) {}
}
