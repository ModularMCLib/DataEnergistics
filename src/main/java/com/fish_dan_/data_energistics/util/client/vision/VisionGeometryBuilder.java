package com.fish_dan_.data_energistics.util.client.vision;

import net.minecraft.core.Direction;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import lombok.RequiredArgsConstructor;

import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Pure snapshot geometry; never reads a world or invokes Minecraft's uninterruptible bulk shape walkers. */
@RequiredArgsConstructor
@OnlyIn(Dist.CLIENT)
public final class VisionGeometryBuilder {

    private final VisionSectionSnapshot snapshot;
    private final BooleanSupplier cancelled;
    private final Consumer<VisionGeometryBatch> output;

    public void build() {
        var contours = new SonarSurfaceContours(snapshot, cancelled);
        try (var thermal = new Batches(VisionGeometryBatch.Kind.THERMAL, output);
                var surfaces = new Batches(VisionGeometryBatch.Kind.SONAR_SURFACE, output);
                var lines = new Batches(VisionGeometryBatch.Kind.SONAR_LINES, output)) {
            for (int index = 0; index < 4096; index++) {
                checkCancelled();
                int bx = index & 15, bz = index >> 4 & 15, by = index >> 8;
                VisionShape shape = snapshot.shape(bx, by, bz);
                int visible = snapshot.visibleFaces()[index], temperature = snapshot.temperatures()[index];
                if (shape == VisionShape.UNSUPPORTED) continue;
                boolean sonar = (snapshot.modes() & VisionSectionSnapshot.SONAR) != 0 && !shape.empty();
                if (shape.empty()) {
                    if (temperature == 0) continue;
                    shape = VisionShape.BLOCK;
                }
                if (visible == 0 && shape == VisionShape.BLOCK) continue;
                for (int x = 0; x < shape.x().length - 1; x++) for (int y = 0; y < shape.y().length - 1; y++) for (int z = 0; z < shape.z().length - 1; z++) {
                    if ((z & 63) == 0) checkCancelled();
                    if (!shape.filled(x, y, z)) continue;
                    float a = (float) (bx + shape.x()[x]), b = (float) (by + shape.y()[y]), c = (float) (bz + shape.z()[z]);
                    float d = (float) (bx + shape.x()[x + 1]), e = (float) (by + shape.y()[y + 1]), f = (float) (bz + shape.z()[z + 1]);
                    if (!shape.filled(x - 1, y, z) && (shape.x()[x] != 0 || visible(visible, Direction.WEST))) face(contours, thermal, surfaces, sonar, temperature, bx, by, bz, a, b, c, a, b, f, a, e, f, a, e, c);
                    if (!shape.filled(x + 1, y, z) && (shape.x()[x + 1] != 1 || visible(visible, Direction.EAST))) face(contours, thermal, surfaces, sonar, temperature, bx, by, bz, d, b, f, d, b, c, d, e, c, d, e, f);
                    if (!shape.filled(x, y - 1, z) && (shape.y()[y] != 0 || visible(visible, Direction.DOWN))) face(contours, thermal, surfaces, sonar, temperature, bx, by, bz, a, b, c, d, b, c, d, b, f, a, b, f);
                    if (!shape.filled(x, y + 1, z) && (shape.y()[y + 1] != 1 || visible(visible, Direction.UP))) face(contours, thermal, surfaces, sonar, temperature, bx, by, bz, a, e, f, d, e, f, d, e, c, a, e, c);
                    if (!shape.filled(x, y, z - 1) && (shape.z()[z] != 0 || visible(visible, Direction.NORTH))) face(contours, thermal, surfaces, sonar, temperature, bx, by, bz, d, b, c, a, b, c, a, e, c, d, e, c);
                    if (!shape.filled(x, y, z + 1) && (shape.z()[z + 1] != 1 || visible(visible, Direction.SOUTH))) face(contours, thermal, surfaces, sonar, temperature, bx, by, bz, a, b, f, d, b, f, d, e, f, a, e, f);
                }
            }
            contours.write(edge -> {
                checkCancelled();
                lines.line(edge.x1(), edge.y1(), edge.z1(), edge.x2(), edge.y2(), edge.z2());
            });
            thermal.flush();
            surfaces.flush();
            lines.flush();
        }
    }

    private static boolean visible(int faces, Direction direction) {
        return (faces & 1 << direction.ordinal()) != 0;
    }

    private static void face(SonarSurfaceContours contours, Batches thermal, Batches surfaces, boolean sonar, int temperature, int bx, int by, int bz,
                             float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3, float x4, float y4, float z4) {
        if (temperature > 0) thermal.quad(0xff000000 | temperature << 16, x1, y1, z1, x2, y2, z2, x3, y3, z3, x4, y4, z4);
        if (!sonar) return;
        surfaces.quad(0x1c41c8f0, x1, y1, z1, x2, y2, z2, x3, y3, z3, x4, y4, z4);
        contours.add(bx, by, bz, x1, y1, z1, x2, y2, z2);
        contours.add(bx, by, bz, x2, y2, z2, x3, y3, z3);
        contours.add(bx, by, bz, x3, y3, z3, x4, y4, z4);
        contours.add(bx, by, bz, x4, y4, z4, x1, y1, z1);
    }

    private void checkCancelled() {
        if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted()) throw new CancellationException();
    }

    private static final class Batches implements AutoCloseable {

        private static final int MAX_VERTICES = 8192;
        private final VisionGeometryBatch.Kind kind;
        private final Consumer<VisionGeometryBatch> output;
        private ByteBufferBuilder bytes = new ByteBufferBuilder(MAX_VERTICES * 16);
        private BufferBuilder builder;
        private int vertices;

        private Batches(VisionGeometryBatch.Kind kind, Consumer<VisionGeometryBatch> output) {
            this.kind = kind;
            this.output = output;
            builder = builder();
        }

        private BufferBuilder builder() {
            return new BufferBuilder(bytes, kind == VisionGeometryBatch.Kind.SONAR_LINES ? VertexFormat.Mode.DEBUG_LINES : VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        }

        private void quad(int color, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3, float x4, float y4, float z4) {
            builder.addVertex(x1, y1, z1).setColor(color);
            builder.addVertex(x2, y2, z2).setColor(color);
            builder.addVertex(x3, y3, z3).setColor(color);
            builder.addVertex(x4, y4, z4).setColor(color);
            vertices += 4;
            if (vertices >= MAX_VERTICES) flush();
        }

        private void line(float x1, float y1, float z1, float x2, float y2, float z2) {
            builder.addVertex(x1, y1, z1).setColor(65, 200, 240, 210);
            builder.addVertex(x2, y2, z2).setColor(65, 200, 240, 210);
            vertices += 2;
            if (vertices >= MAX_VERTICES) flush();
        }

        private void flush() {
            if (vertices == 0) return;
            var batch = new VisionGeometryBatch(kind, bytes, builder.buildOrThrow());
            bytes = new ByteBufferBuilder(MAX_VERTICES * 16);
            builder = builder();
            vertices = 0;
            output.accept(batch);
        }

        @Override
        public void close() {
            bytes.close();
        }
    }
}
