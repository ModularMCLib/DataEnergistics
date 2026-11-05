package com.fish_dan_.data_energistics.client.weapon.platform.vision;

import com.fish_dan_.data_energistics.client.weapon.platform.vision.sonar.SonarSurfaceContours;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import org.jspecify.annotations.Nullable;

/** Immutable section-local GPU geometry. Construction and disposal stay on the client render thread. */
record VisionSectionMesh(BlockPos origin, int modes, @Nullable VertexBuffer thermal, @Nullable VertexBuffer sonarSurface, @Nullable VertexBuffer sonar) implements AutoCloseable {

    static final int THERMAL = 1;
    static final int SONAR = 2;
    private static final Direction[] DIRECTIONS = Direction.values();

    static VisionSectionMesh build(ClientLevel level, long sectionId, LevelChunkSection section, int modes) {
        BlockPos origin = SectionPos.of(sectionId).origin();
        var contours = new SonarSurfaceContours(level, origin);
        try (var heatBytes = new ByteBufferBuilder(32768); var surfaceBytes = new ByteBufferBuilder(32768); var lineBytes = new ByteBufferBuilder(32768)) {
            var heat = new BufferBuilder(heatBytes, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            var surfaces = new BufferBuilder(surfaceBytes, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            var lines = new BufferBuilder(lineBytes, VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
            var position = new BlockPos.MutableBlockPos();
            for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                BlockState state = section.getBlockState(x, y, z);
                if (state.isAir()) continue;
                position.setWithOffset(origin, x, y, z);
                int visible = exposedFaces(level, position, state);
                VoxelShape shape = state.getShape(level, position);
                if (visible == 0 && shape == Shapes.block()) continue;
                int temperature = (modes & THERMAL) != 0 ? ThermalSources.temperature(state, level, position) : 0;
                if (shape.isEmpty()) {
                    var fluid = state.getFluidState();
                    if (!fluid.isEmpty()) shape = Shapes.box(0, 0, 0, 1, fluid.getHeight(level, position), 1);
                }
                if ((modes & SONAR) != 0 && (visible != 0 || shape != Shapes.block())) contours.add(position, shape);
                int bx = x, by = y, bz = z;
                if ((modes & SONAR) != 0) shape.forAllBoxes((x1, y1, z1, x2, y2, z2) -> faces(surfaces, bx, by, bz, x1, y1, z1, x2, y2, z2, visible, 0x1c41c8f0));
                if (shape.isEmpty() && temperature > 0) shape = Shapes.block();
                if (temperature > 0) shape.forAllBoxes((x1, y1, z1, x2, y2, z2) -> faces(heat, bx, by, bz, x1, y1, z1, x2, y2, z2, visible, 0xff000000 | temperature << 16));
            }
            contours.write(lines);
            return new VisionSectionMesh(origin, modes, upload(heat), upload(surfaces), upload(lines));
        }
    }

    private static int exposedFaces(ClientLevel level, BlockPos position, BlockState state) {
        int faces = 0;
        var fluid = state.getFluidState();
        for (Direction direction : DIRECTIONS) {
            BlockPos neighbor = position.relative(direction);
            if (!level.getChunkSource().hasChunk(neighbor.getX() >> 4, neighbor.getZ() >> 4)) faces |= 1 << direction.ordinal();
            else if ((fluid.isEmpty() || !fluid.getType().isSame(level.getFluidState(neighbor).getType())) && Block.shouldRenderFace(state, level, position, direction, neighbor)) faces |= 1 << direction.ordinal();
        }
        return faces;
    }

    private static boolean visible(int faces, Direction direction) {
        return (faces & 1 << direction.ordinal()) != 0;
    }

    private static void faces(BufferBuilder builder, int x, int y, int z, double x1, double y1, double z1, double x2, double y2, double z2, int visible, int color) {
        float a = (float) (x + x1), b = (float) (y + y1), c = (float) (z + z1), d = (float) (x + x2), e = (float) (y + y2), f = (float) (z + z2);
        if (x1 > 0 || visible(visible, Direction.WEST)) quad(builder, color, a, b, c, a, b, f, a, e, f, a, e, c);
        if (x2 < 1 || visible(visible, Direction.EAST)) quad(builder, color, d, b, f, d, b, c, d, e, c, d, e, f);
        if (y1 > 0 || visible(visible, Direction.DOWN)) quad(builder, color, a, b, c, d, b, c, d, b, f, a, b, f);
        if (y2 < 1 || visible(visible, Direction.UP)) quad(builder, color, a, e, f, d, e, f, d, e, c, a, e, c);
        if (z1 > 0 || visible(visible, Direction.NORTH)) quad(builder, color, d, b, c, a, b, c, a, e, c, d, e, c);
        if (z2 < 1 || visible(visible, Direction.SOUTH)) quad(builder, color, a, b, f, d, b, f, d, e, f, a, e, f);
    }

    private static void quad(BufferBuilder builder, int color, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3, float x4, float y4, float z4) {
        builder.addVertex(x1, y1, z1).setColor(color);
        builder.addVertex(x2, y2, z2).setColor(color);
        builder.addVertex(x3, y3, z3).setColor(color);
        builder.addVertex(x4, y4, z4).setColor(color);
    }

    private static @Nullable VertexBuffer upload(BufferBuilder builder) {
        var mesh = builder.build();
        if (mesh == null) return null;
        var buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
        try (mesh) {
            buffer.bind();
            buffer.upload(mesh);
        } finally {
            VertexBuffer.unbind();
        }
        return buffer;
    }

    @Override
    public void close() {
        if (thermal != null) thermal.close();
        if (sonarSurface != null) sonarSurface.close();
        if (sonar != null) sonar.close();
    }
}
