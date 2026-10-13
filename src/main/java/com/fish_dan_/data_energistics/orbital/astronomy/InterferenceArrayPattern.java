package com.fish_dan_.data_energistics.orbital.astronomy;

import com.fish_dan_.data_energistics.configuration.DataEnergisticsConfiguration;
import com.fish_dan_.data_energistics.registry.DEBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;

import it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;

import java.util.Comparator;

/**
 * Validates the compact core pedestal and discovers mirrors through bounded horizontal waveguide paths.
 */
public final class InterferenceArrayPattern {

    private static final int COMPACT_CORE_RADIUS = 1;
    private static final int COMPACT_CORE_HEIGHT = 2;
    private static final int LEGACY_CORE_RADIUS = 2;
    private static final int LEGACY_CORE_HEIGHT = 3;
    private static final int PORT_HEIGHT = 1;
    private static final Comparator<BlockPos> POSITION_ORDER = Comparator.comparingInt((BlockPos pos) -> pos.getY())
            .thenComparingInt(BlockPos::getZ)
            .thenComparingInt(BlockPos::getX);

    private InterferenceArrayPattern() {}

    /**
     * Returns whether the controller occupies either the compact 3x3x2 pedestal or the original 5x5x3 base. The
     * compact form is preferred for new builds; the legacy form remains valid for existing worlds.
     */
    public static boolean hasValidCoreBase(ServerLevel level, BlockPos corePos) {
        return hasValidBase(level, corePos, COMPACT_CORE_RADIUS, COMPACT_CORE_HEIGHT) || hasValidBase(level, corePos, LEGACY_CORE_RADIUS, LEGACY_CORE_HEIGHT);
    }

    private static boolean hasValidBase(ServerLevel level, BlockPos corePos, int radius, int height) {
        for (int y = 0; y < height; y++) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos current = corePos.offset(x, y, z);
                    if (x == 0 && y == 0 && z == 0) {
                        if (!level.getBlockState(current).is(DEBlocks.INTERFERENCE_ARRAY_CORE.get())) {
                            return false;
                        }
                    } else if (isPortOffset(x, y, z, radius)) {
                        if (!level.getBlockState(current).is(DEBlocks.CELESTIAL_WAVEGUIDE.get())) {
                            return false;
                        }
                    } else if (!level.getBlockState(current).is(DEBlocks.DATA_FRAMEWORK.get())) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /**
     * Returns structurally valid mirror centers reachable from one of the four core ports within the configured path
     * length. A waveguide may terminate beside any outer mirror panel, allowing flat radial arms without a floating
     * vertical mast. Results are nearest-first and then position-stable so claim order is deterministic.
     */
    public static LongList findConnectedMirrors(
                                                ServerLevel level,
                                                BlockPos corePos,
                                                DataEnergisticsConfiguration.AstronomySchema settings) {
        if (!hasValidCoreBase(level, corePos)) {
            return new LongArrayList();
        }

        LongArrayFIFOQueue pendingPositions = new LongArrayFIFOQueue();
        IntArrayFIFOQueue pendingDistances = new IntArrayFIFOQueue();
        LongSet visitedWaveguides = new LongOpenHashSet();
        int portRadius = hasValidBase(level, corePos, COMPACT_CORE_RADIUS, COMPACT_CORE_HEIGHT) ? COMPACT_CORE_RADIUS : LEGACY_CORE_RADIUS;
        for (long offset : portOffsets(portRadius)) {
            long port = corePos.offset(BlockPos.of(offset)).asLong();
            pendingPositions.enqueue(port);
            pendingDistances.enqueue(0);
            visitedWaveguides.add(port);
        }

        Long2IntOpenHashMap mirrorDistances = new Long2IntOpenHashMap();
        mirrorDistances.defaultReturnValue(Integer.MAX_VALUE);
        int maximumLength = settings.highTierWaveguidePathLength;
        while (!pendingPositions.isEmpty()) {
            long packedPosition = pendingPositions.dequeueLong();
            int distance = pendingDistances.dequeueInt();
            BlockPos position = BlockPos.of(packedPosition);
            for (Direction direction : Direction.values()) {
                BlockPos adjacent = position.relative(direction);
                Block adjacentBlock = level.getBlockState(adjacent).getBlock();
                if (adjacentBlock == DEBlocks.ASTRONOMICAL_MIRROR.get() &&
                        isValidMirror(level, corePos, adjacent, settings)) {
                    long mirrorPosition = adjacent.asLong();
                    mirrorDistances.put(mirrorPosition, Math.min(mirrorDistances.get(mirrorPosition), distance));
                } else if (adjacentBlock == DEBlocks.ASTRONOMICAL_MIRROR_PANEL.get()) {
                    for (int x = -1; x <= 1; x++) {
                        for (int z = -1; z <= 1; z++) {
                            BlockPos candidate = adjacent.offset(x, 0, z);
                            if (level.getBlockState(candidate).is(DEBlocks.ASTRONOMICAL_MIRROR.get()) &&
                                    isValidMirror(level, corePos, candidate, settings)) {
                                long mirrorPosition = candidate.asLong();
                                mirrorDistances.put(mirrorPosition, Math.min(mirrorDistances.get(mirrorPosition), distance));
                            }
                        }
                    }
                }
                if (distance >= maximumLength ||
                        !level.getBlockState(adjacent).is(DEBlocks.CELESTIAL_WAVEGUIDE.get())) {
                    continue;
                }
                long adjacentPosition = adjacent.asLong();
                if (visitedWaveguides.add(adjacentPosition)) {
                    pendingPositions.enqueue(adjacentPosition);
                    pendingDistances.enqueue(distance + 1);
                }
            }
        }

        LongArrayList mirrors = new LongArrayList(mirrorDistances.size());
        for (long position : mirrorDistances.keySet()) {
            mirrors.add(position);
        }
        mirrors.sort((left, right) -> {
            int distanceComparison = Integer.compare(mirrorDistances.get(left), mirrorDistances.get(right));
            return distanceComparison != 0 ? distanceComparison : POSITION_ORDER.compare(BlockPos.of(left), BlockPos.of(right));
        });
        return mirrors;
    }

    private static boolean isPortOffset(int x, int y, int z, int radius) {
        return y == PORT_HEIGHT &&
                ((Math.abs(x) == radius && z == 0) || (x == 0 && Math.abs(z) == radius));
    }

    private static LongList portOffsets(int radius) {
        return LongArrayList.wrap(new long[] {
                new BlockPos(radius, PORT_HEIGHT, 0).asLong(),
                new BlockPos(-radius, PORT_HEIGHT, 0).asLong(),
                new BlockPos(0, PORT_HEIGHT, radius).asLong(),
                new BlockPos(0, PORT_HEIGHT, -radius).asLong()
        });
    }

    private static boolean isValidMirror(
                                         ServerLevel level,
                                         BlockPos corePos,
                                         BlockPos mirrorCenter,
                                         DataEnergisticsConfiguration.AstronomySchema settings) {
        int deltaX = mirrorCenter.getX() - corePos.getX();
        int deltaZ = mirrorCenter.getZ() - corePos.getZ();
        long horizontalDistanceSquared = (long) deltaX * deltaX + (long) deltaZ * deltaZ;
        long maximumHorizontalDistance = settings.highTierMirrorHorizontalRange;
        if (horizontalDistanceSquared > maximumHorizontalDistance * maximumHorizontalDistance ||
                Math.abs(mirrorCenter.getY() - corePos.getY()) > settings.highTierMirrorVerticalRange) {
            return false;
        }

        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                BlockPos aperture = mirrorCenter.offset(x, 0, z);
                boolean center = x == 0 && z == 0;
                if (center) {
                    if (!level.getBlockState(aperture).is(DEBlocks.ASTRONOMICAL_MIRROR.get())) {
                        return false;
                    }
                } else if (!level.getBlockState(aperture).is(DEBlocks.ASTRONOMICAL_MIRROR_PANEL.get())) {
                    return false;
                }
                if (!level.canSeeSky(aperture.above())) {
                    return false;
                }
            }
        }
        return true;
    }
}
