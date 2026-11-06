package com.fish_dan_.data_energistics.client.weapon.platform.vision;

import com.fish_dan_.data_energistics.client.registry.DEShaders;
import com.fish_dan_.data_energistics.util.client.vision.ThermalSources;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.model.pipeline.VertexConsumerWrapper;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceLinkedOpenHashSet;
import lombok.RequiredArgsConstructor;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

/** Renders thermal entities every frame and keeps world-space sonar geometry between pulses. */
@RequiredArgsConstructor
final class VisionEntityMasks implements MultiBufferSource, AutoCloseable {

    private static final int THERMAL_PASS = 1;
    private static final int SONAR_PASS = 2;
    private static final long SCAN_BUDGET_NANOS = 750_000;
    private static int pass;
    private final RenderTarget target;
    private final boolean thermal;
    private final Reference2ObjectOpenHashMap<RenderType, MaskBatch> batches = new Reference2ObjectOpenHashMap<>();
    private final ReferenceLinkedOpenHashSet<MaskBatch> pending = new ReferenceLinkedOpenHashSet<>();
    private final ReferenceLinkedOpenHashSet<MaskBatch> scanBatches = new ReferenceLinkedOpenHashSet<>();
    private final Matrix4f scanView = new Matrix4f();
    private final IntOpenHashSet scannedEntities = new IntOpenHashSet();
    private Vec3 scanOrigin = Vec3.ZERO;
    private boolean scanCaptured;
    private boolean scanComplete;
    private long scanTick = Long.MIN_VALUE;
    private int color;

    static boolean rendering() {
        return pass != 0;
    }

    static boolean sonarRendering() {
        return (pass & SONAR_PASS) != 0;
    }

    void draw(RenderLevelStageEvent event, Minecraft client, ClientLevel level, Vec3 player) {
        Vec3 camera = event.getCamera().getPosition();
        var poses = new PoseStack();
        var dispatcher = client.getEntityRenderDispatcher();
        pass = THERMAL_PASS;
        try {
            for (Entity entity : level.entitiesForRendering()) {
                if (entity == client.getCameraEntity() && client.options.getCameraType().isFirstPerson()) continue;
                if (!ThermalSources.hasHeat(entity)) continue;
                if (!event.getFrustum().isVisible(entity.getBoundingBoxForCulling())) continue;
                color = 0xff000000 | ThermalSources.temperature(entity) << 16;
                float partial = level.tickRateManager().isEntityFrozen(entity) ? 1 : event.getPartialTick().getGameTimeDeltaPartialTick(false);
                var renderer = dispatcher.getRenderer(entity);
                Vec3 position = entity.getPosition(partial).subtract(camera).add(renderer.getRenderOffset(entity, partial));
                poses.pushPose();
                try {
                    poses.translate(position.x, position.y, position.z);
                    renderer.render(entity, Mth.lerp(partial, entity.yRotO, entity.getYRot()), partial, poses, this, LightTexture.FULL_BRIGHT);
                } finally {
                    poses.popPose();
                }
            }
            drawPending(camera, player);
        } finally {
            pass = 0;
        }
    }

    boolean hasScan() {
        return scanCaptured;
    }

    void captureScan(RenderLevelStageEvent event, Minecraft client, ClientLevel level, Vec3 scanCenter, double radiusSquared, boolean startedScan) {
        if (startedScan || !scanCaptured) {
            clearScan();
            scanOrigin = scanCenter;
            scanCaptured = true;
        }
        if (scanComplete || scanTick == level.getGameTime()) return;
        long deadline = System.nanoTime() + SCAN_BUDGET_NANOS;
        boolean finished = true;
        var poses = new PoseStack();
        var dispatcher = client.getEntityRenderDispatcher();
        pass = SONAR_PASS;
        try {
            for (Entity entity : level.entitiesForRendering()) {
                if (entity == client.getCameraEntity() && client.options.getCameraType().isFirstPerson()) continue;
                if (scannedEntities.contains(entity.getId()) || entity.getBoundingBox().distanceToSqr(scanOrigin) > radiusSquared) continue;
                if (System.nanoTime() >= deadline) {
                    finished = false;
                    break;
                }
                scannedEntities.add(entity.getId());
                color = 0xffffffff;
                float partial = level.tickRateManager().isEntityFrozen(entity) ? 1 : event.getPartialTick().getGameTimeDeltaPartialTick(false);
                var renderer = dispatcher.getRenderer(entity);
                Vec3 position = entity.getPosition(partial).add(renderer.getRenderOffset(entity, partial));
                poses.pushPose();
                try {
                    poses.translate(position.x - scanOrigin.x, position.y - scanOrigin.y, position.z - scanOrigin.z);
                    renderer.render(entity, Mth.lerp(partial, entity.yRotO, entity.getYRot()), partial, poses, this, LightTexture.FULL_BRIGHT);
                } finally {
                    poses.popPose();
                }
            }
            var iterator = pending.iterator();
            while (iterator.hasNext() && System.nanoTime() < deadline) {
                MaskBatch batch = iterator.next();
                iterator.remove();
                if (batch.capture()) scanBatches.add(batch);
            }
            if (finished) {
                scanTick = level.getGameTime();
                scanComplete = pending.isEmpty() && radiusSquared >= VisionSectionCache.SONAR_RADIUS_SQUARED;
            }
        } catch (RuntimeException | Error failure) {
            clearScan();
            throw failure;
        } finally {
            pass = 0;
        }
    }

    void drawScan(RenderLevelStageEvent event, Vec3 player) {
        if (scanBatches.isEmpty()) return;
        ShaderInstance shader = DEShaders.getVisionEntity();
        shader.safeGetUniform("CameraOffset").set((float) (scanOrigin.x - player.x), (float) (scanOrigin.y - player.y), (float) (scanOrigin.z - player.z));
        shader.safeGetUniform("ScanRadiusSquared").set((float) VisionSectionCache.SONAR_RADIUS_SQUARED);
        Vec3 camera = event.getCamera().getPosition();
        Matrix4f view = scanView.set(event.getModelViewMatrix()).translate(
                (float) (scanOrigin.x - camera.x), (float) (scanOrigin.y - camera.y), (float) (scanOrigin.z - camera.z));
        try {
            for (var batch : scanBatches) batch.drawScan(view, event.getProjectionMatrix(), shader);
        } finally {
            VertexBuffer.unbind();
        }
    }

    void clearScan() {
        scanCaptured = false;
        scanComplete = false;
        scanTick = Long.MIN_VALUE;
        scanOrigin = Vec3.ZERO;
        scannedEntities.clear();
        pending.clear();
        scanBatches.clear();
        batches.values().forEach(MaskBatch::clearScan);
    }

    @Override
    @SuppressWarnings("resource") // Batches own the native allocators until the framebuffer is closed.
    public VertexConsumer getBuffer(RenderType original) {
        boolean textured = original.format().contains(VertexFormatElement.UV0);
        return batches.computeIfAbsent(original, MaskBatch::new).consumer(color, textured);
    }

    private void drawPending(Vec3 camera, Vec3 player) {
        var shader = DEShaders.getVisionEntity();
        shader.safeGetUniform("CameraOffset").set((float) (camera.x - player.x), (float) (camera.y - player.y), (float) (camera.z - player.z));
        shader.safeGetUniform("ScanRadiusSquared").set(0f);
        for (var batch : pending) batch.draw();
        pending.clear();
    }

    private RenderType maskType(RenderType original) {
        return new RenderType("platform_vision_entity_mask", DefaultVertexFormat.POSITION_TEX_COLOR, original.mode(), 1536, false, false, () -> {
            original.setupRenderState();
            target.bindWrite(false);
            RenderSystem.setShader(DEShaders::getVisionEntity);
            DEShaders.getVisionEntity().safeGetUniform("Textured").set(original.format().contains(VertexFormatElement.UV0) ? 1f : 0f);
            RenderSystem.disableBlend();
            RenderSystem.disableCull();
            RenderSystem.colorMask(true, true, true, true);
            RenderSystem.depthMask(thermal);
            if (thermal) {
                RenderSystem.enableDepthTest();
                RenderSystem.depthFunc(GL11.GL_LEQUAL);
            } else RenderSystem.disableDepthTest();
        }, original::clearRenderState) {};
    }

    @Override
    public void close() {
        batches.values().forEach(MaskBatch::close);
        batches.clear();
        pending.clear();
        scanBatches.clear();
        scannedEntities.clear();
        scanCaptured = false;
    }

    private final class MaskBatch implements AutoCloseable {

        private final RenderType type;
        private ByteBufferBuilder bytes = new ByteBufferBuilder(32768);
        private BufferBuilder builder;
        private final ObjectArrayList<VertexBuffer> scanBuffers = new ObjectArrayList<>();

        private MaskBatch(RenderType original) {
            type = maskType(original);
            builder = new BufferBuilder(bytes, type.mode(), type.format());
        }

        private VertexConsumer consumer(int color, boolean textured) {
            pending.add(this);
            return new MaskColor(builder, color, textured);
        }

        private void draw() {
            var mesh = builder.build();
            if (mesh != null) type.draw(mesh);
            builder = new BufferBuilder(bytes, type.mode(), type.format());
        }

        private boolean capture() {
            var mesh = builder.build();
            builder = new BufferBuilder(bytes, type.mode(), type.format());
            if (mesh == null) return false;
            try (mesh) {
                VertexBuffer scanBuffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
                try {
                    scanBuffer.bind();
                    scanBuffer.upload(mesh);
                    scanBuffers.add(scanBuffer);
                } catch (RuntimeException | Error failure) {
                    scanBuffer.close();
                    throw failure;
                }
            } finally {
                VertexBuffer.unbind();
            }
            return true;
        }

        private void drawScan(Matrix4f view, Matrix4f projection, ShaderInstance shader) {
            type.setupRenderState();
            try {
                shader.setDefaultUniforms(type.mode(), view, projection, Minecraft.getInstance().getWindow());
                shader.apply();
                for (VertexBuffer scanBuffer : scanBuffers) {
                    scanBuffer.bind();
                    scanBuffer.draw();
                }
            } finally {
                shader.clear();
                type.clearRenderState();
            }
        }

        private void clearScan() {
            scanBuffers.forEach(VertexBuffer::close);
            scanBuffers.clear();
            bytes.close();
            bytes = new ByteBufferBuilder(32768);
            builder = new BufferBuilder(bytes, type.mode(), type.format());
        }

        @Override
        public void close() {
            scanBuffers.forEach(VertexBuffer::close);
            scanBuffers.clear();
            bytes.close();
        }
    }

    private static final class MaskColor extends VertexConsumerWrapper {

        private final int color;
        private final boolean textured;

        private MaskColor(VertexConsumer parent, int color, boolean textured) {
            super(parent);
            this.color = color;
            this.textured = textured;
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            parent.addVertex(x, y, z).setColor(color);
            if (!textured) parent.setUv(0, 0);
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            return this;
        }
    }
}
