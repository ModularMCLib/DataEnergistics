package com.fish_dan_.data_energistics.utils.vision;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import org.jspecify.annotations.Nullable;

/** Resumable world reads. The same client-thread deadline also bounds copying a single complex shape. */
@OnlyIn(Dist.CLIENT)
public final class VisionSectionCapture {

    private static final Direction[] DIRECTIONS = Direction.values();
    private final long origin;
    private final int modes;
    private final int baseChunkX;
    private final int baseChunkZ;
    private final VisionShape[] shapes = new VisionShape[18 * 18 * 18];
    private final int[] temperatures = new int[4096];
    private final int[] visible = new int[4096];
    private final BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
    private final BlockPos.MutableBlockPos neighbor = new BlockPos.MutableBlockPos();
    private static final ChunkEntry UNQUERIED = new ChunkEntry(false, null);
    private final ChunkEntry[] chunks = { UNQUERIED, UNQUERIED, UNQUERIED, UNQUERIED, UNQUERIED, UNQUERIED, UNQUERIED, UNQUERIED, UNQUERIED };
    private @Nullable PendingBlock pending;
    private int cursor;

    public VisionSectionCapture(long section, int modes) {
        origin = BlockPos.asLong(SectionPos.sectionToBlockCoord(SectionPos.x(section)), SectionPos.sectionToBlockCoord(SectionPos.y(section)), SectionPos.sectionToBlockCoord(SectionPos.z(section)));
        baseChunkX = BlockPos.getX(origin) >> 4;
        baseChunkZ = BlockPos.getZ(origin) >> 4;
        this.modes = modes;
    }

    public @Nullable VisionSectionSnapshot advance(ClientLevel level, VisionShapeCache cache, long deadline) {
        while (cursor < shapes.length && System.nanoTime() < deadline) {
            int x = cursor % 18 - 1, z = cursor / 18 % 18 - 1, y = cursor / (18 * 18) - 1;
            position.set(BlockPos.getX(origin) + x, BlockPos.getY(origin) + y, BlockPos.getZ(origin) + z);
            if (pending == null) {
                LevelChunk chunk = loadedChunk(level, position.getX() >> 4, position.getZ() >> 4);
                if (chunk == null) {
                    shapes[cursor++] = VisionShape.EMPTY;
                    continue;
                }
                BlockState state = chunk.getBlockState(position);
                if (state.isAir()) {
                    shapes[cursor++] = VisionShape.EMPTY;
                    continue;
                }
                pending = new PendingBlock(state, state.getShape(level, position));
            }
            BlockState state = pending.state();
            VisionShape shape = cache.capture(pending.shape(), deadline);
            if (shape == null) return null;
            pending = null;
            if (shape == VisionShape.UNSUPPORTED) {
                shapes[cursor++] = shape;
                continue;
            }
            var fluid = state.getFluidState();
            if (shape.empty() && !fluid.isEmpty()) shape = VisionShape.box(fluid.getHeight(level, position));
            shapes[cursor++] = shape;
            if (x < 0 || y < 0 || z < 0 || x > 15 || y > 15 || z > 15) continue;
            int index = (y * 16 + z) * 16 + x;
            visible[index] = exposedFaces(level, position, state);
            if ((modes & VisionSectionSnapshot.THERMAL) != 0 && (visible[index] != 0 || shape != VisionShape.BLOCK)) temperatures[index] = ThermalSources.temperature(state, level, position);
        }
        return cursor == shapes.length ? new VisionSectionSnapshot(origin, modes, shapes, temperatures, visible) : null;
    }

    private int exposedFaces(ClientLevel level, BlockPos position, BlockState state) {
        int faces = 0;
        var fluid = state.getFluidState();
        for (Direction direction : DIRECTIONS) {
            neighbor.setWithOffset(position, direction);
            LevelChunk chunk = loadedChunk(level, neighbor.getX() >> 4, neighbor.getZ() >> 4);
            if (chunk == null) faces |= 1 << direction.ordinal();
            else {
                BlockState adjacent = chunk.getBlockState(neighbor);
                if ((fluid.isEmpty() || !fluid.getType().isSame(adjacent.getFluidState().getType())) && Block.shouldRenderFace(state, level, position, direction, neighbor)) faces |= 1 << direction.ordinal();
            }
        }
        return faces;
    }

    private @Nullable LevelChunk loadedChunk(ClientLevel level, int chunkX, int chunkZ) {
        int chunkIndex = (chunkX - baseChunkX + 1) * 3 + chunkZ - baseChunkZ + 1;
        ChunkEntry entry = chunks[chunkIndex];
        if (!entry.queried()) {
            entry = new ChunkEntry(true, level.getChunkSource().getChunk(chunkX, chunkZ, false));
            chunks[chunkIndex] = entry;
        }
        return entry.chunk();
    }

    private record PendingBlock(BlockState state, VoxelShape shape) {}

    private record ChunkEntry(boolean queried, @Nullable LevelChunk chunk) {}
}
