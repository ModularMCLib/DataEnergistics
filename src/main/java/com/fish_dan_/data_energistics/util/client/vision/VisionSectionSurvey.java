package com.fish_dan_.data_energistics.util.client.vision;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import lombok.Getter;
import org.jspecify.annotations.Nullable;

/** Resumable near-to-far spiral. Visits loaded sections only, without allocating or sorting a world-sized queue. */
@OnlyIn(Dist.CLIENT)
public final class VisionSectionSurvey {

    public static final long NO_SECTION = Long.MIN_VALUE;
    private final int centerX;
    private final int centerZ;
    private final int centerY;
    private int remaining;
    private int x;
    private int z;
    private int dx;
    private int dz = -1;
    private int sectionCursor;
    private @Nullable LevelChunk chunk;
    @Getter
    private boolean finished;

    public VisionSectionSurvey(int centerX, int centerY, int centerZ, int radius) {
        this.centerX = centerX;
        this.centerY = centerY;
        this.centerZ = centerZ;
        remaining = (radius * 2 + 1) * (radius * 2 + 1);
    }

    public long next(ClientLevel level, long deadline) {
        while (System.nanoTime() < deadline) {
            if (chunk == null) {
                if (remaining == 0) {
                    finished = true;
                    return NO_SECTION;
                }
                chunk = level.getChunkSource().getChunk(centerX + x, centerZ + z, false);
                remaining--;
                if (x == z || x < 0 && x == -z || x > 0 && x == 1 - z) {
                    int previous = dx;
                    dx = -dz;
                    dz = previous;
                }
                x += dx;
                z += dz;
                sectionCursor = 0;
                if (chunk == null) continue;
            }
            int count = chunk.getSections().length;
            if (sectionCursor >= count * 2 - 1) {
                chunk = null;
                continue;
            }
            int delta = (sectionCursor + 1) / 2 * ((sectionCursor & 1) == 0 ? -1 : 1);
            sectionCursor++;
            int index = Math.clamp(level.getSectionIndexFromSectionY(centerY), 0, count - 1) + delta;
            if (index < 0 || index >= count || chunk.getSection(index).hasOnlyAir()) continue;
            return SectionPos.asLong(chunk.getPos().x, level.getSectionYFromSectionIndex(index), chunk.getPos().z);
        }
        return NO_SECTION;
    }
}
