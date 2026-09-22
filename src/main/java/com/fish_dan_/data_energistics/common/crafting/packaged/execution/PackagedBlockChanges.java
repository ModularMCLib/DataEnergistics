package com.fish_dan_.data_energistics.common.crafting.packaged.execution;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import org.jspecify.annotations.Nullable;

/** Exact synchronous native growth changes, used to undo a tree without touching neighbouring trees. */
public final class PackagedBlockChanges {

    private static final ThreadLocal<@Nullable Capture> CURRENT = new ThreadLocal<>();

    private PackagedBlockChanges() {}

    public static ObjectList<Change> record(ServerLevel level, Runnable action) {
        var previous = CURRENT.get();
        var capture = new Capture(level, new Object2ObjectLinkedOpenHashMap<>());
        CURRENT.set(capture);
        try {
            action.run();
            return new ObjectArrayList<>(capture.changes().values());
        } finally {
            if (previous == null) CURRENT.remove();
            else CURRENT.set(previous);
        }
    }

    public static void changed(ServerLevel level, BlockPos position, BlockState previous, BlockState current) {
        var capture = CURRENT.get();
        if (capture == null || capture.level() != level || previous == current) return;
        var first = capture.changes().get(position);
        capture.changes().put(position.immutable(), new Change(position.immutable(), first == null ? previous : first.previous(), current));
    }

    public record Change(BlockPos position, BlockState previous, BlockState current) {}

    private record Capture(ServerLevel level, Object2ObjectLinkedOpenHashMap<BlockPos, Change> changes) {}
}
