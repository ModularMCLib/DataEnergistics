package com.fish_dan_.data_energistics.client.render.domain;

import com.fish_dan_.data_energistics.common.weapon.platform.domain.StasisDomain;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

/** Samples only loaded terrain; the visible shell never changes the full-height chunk freeze index. */
final class DomainBoundarySurface {

    private static final int STEP = 4;
    private final StasisDomain domain;
    private final @Nullable LevelChunk[] chunks;
    private final float[] ground;
    private final int samples;
    private final int minX;
    private final int minZ;
    private long nextCheck;
    final int width;
    final double top;
    final double wallHeight;
    final AABB bounds;

    DomainBoundarySurface(ClientLevel level, StasisDomain domain) {
        this.domain = domain;
        width = domain.size() * 16;
        minX = (domain.x() - domain.size() / 2) * 16;
        minZ = (domain.z() - domain.size() / 2) * 16;
        top = domain.y() + 20;
        chunks = new LevelChunk[domain.size() * domain.size()];
        for (int z = 0; z < domain.size(); z++) for (int x = 0; x < domain.size(); x++) chunks[z * domain.size() + x] = level.getChunkSource().getChunkNow((minX >> 4) + x, (minZ >> 4) + z);
        samples = width / STEP + 1;
        ground = new float[samples * samples];
        double bottom = top;
        var position = new BlockPos.MutableBlockPos();
        for (int z = 0; z < samples; z++) for (int x = 0; x < samples; x++) {
            float height = height(x * STEP, z * STEP, position, level.getMinBuildHeight());
            ground[z * samples + x] = height;
            bottom = Math.min(bottom, height);
        }
        wallHeight = Math.max(1, top - bottom);
        bounds = new AABB(minX, bottom, minZ, minX + width, top, minZ + width);
        nextCheck = level.getGameTime() + 20;
    }

    boolean current(ClientLevel level) {
        for (int z = 0; z < domain.size(); z++) for (int x = 0; x < domain.size(); x++) {
            if (chunks[z * domain.size() + x] != level.getChunkSource().getChunkNow((minX >> 4) + x, (minZ >> 4) + z)) return false;
        }
        if (level.getGameTime() < nextCheck) return true;
        nextCheck = level.getGameTime() + 20;
        var position = new BlockPos.MutableBlockPos();
        for (int z = 0; z < samples; z++) for (int x = 0; x < samples; x++) {
            if (ground[z * samples + x] != height(x * STEP, z * STEP, position, level.getMinBuildHeight())) return false;
        }
        return true;
    }

    private float height(int x, int z, BlockPos.MutableBlockPos position, int minimum) {
        x = Math.min(x, width - 1);
        z = Math.min(z, width - 1);
        var chunk = chunks[(z >> 4) * domain.size() + (x >> 4)];
        if (chunk == null) return (float) Math.min(domain.y(), top - 0.1);
        int height = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, x & 15, z & 15) + 1;
        // Under a ceiling, use the ground below the release position rather than a roof above the shell.
        if (height > domain.y() + 1) {
            height = Mth.floor(domain.y());
            position.set(minX + x, height, minZ + z);
            while (height > minimum) {
                var state = chunk.getBlockState(position);
                if (!state.getCollisionShape(chunk, position).isEmpty() || !state.getFluidState().isEmpty()) break;
                position.setY(--height);
            }
            height++;
        }
        return (float) Math.min(height, top - 0.1);
    }

    boolean loaded(Direction face, double u, double v) {
        double x = switch (face) {
            case WEST -> 0;
            case EAST -> width;
            default -> u;
        };
        double z = switch (face) {
            case NORTH -> 0;
            case SOUTH -> width;
            case WEST, EAST -> u;
            default -> v;
        };
        int chunkX = Mth.clamp(Mth.floor(x) >> 4, 0, domain.size() - 1);
        int chunkZ = Mth.clamp(Mth.floor(z) >> 4, 0, domain.size() - 1);
        return chunks[chunkZ * domain.size() + chunkX] != null;
    }

    Vec3 point(Direction face, double u, double v) {
        double x = switch (face) {
            case WEST -> -0.025;
            case EAST -> width + 0.025;
            default -> u;
        };
        double z = switch (face) {
            case NORTH -> -0.025;
            case SOUTH -> width + 0.025;
            case WEST, EAST -> u;
            default -> v;
        };
        double y = top;
        if (face != Direction.UP) {
            double gridX = Mth.clamp(x / STEP, 0, samples - 1.001), gridZ = Mth.clamp(z / STEP, 0, samples - 1.001);
            int a = (int) gridX, b = (int) gridZ;
            double low = Mth.lerp(gridX - a, ground[b * samples + a], ground[b * samples + a + 1]);
            double high = Mth.lerp(gridX - a, ground[(b + 1) * samples + a], ground[(b + 1) * samples + a + 1]);
            double bottom = Mth.lerp(gridZ - b, low, high) + 0.035;
            y = face == Direction.DOWN ? bottom : Mth.lerp(v / wallHeight, bottom, top);
        }
        return new Vec3(x, y - domain.y(), z);
    }

    Vec3 origin() {
        return new Vec3(minX, domain.y(), minZ);
    }
}
