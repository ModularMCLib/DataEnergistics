package com.fish_dan_.data_energistics.client.weapon.platform.vision;

import com.fish_dan_.data_energistics.client.registry.DEShaders;
import com.fish_dan_.data_energistics.util.client.vision.ThermalSources;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.model.pipeline.VertexConsumerWrapper;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import com.mojang.blaze3d.vertex.VertexMultiConsumer;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceLinkedOpenHashSet;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.lwjgl.opengl.GL11;

/** Renders the actual posed models into an isolated mask, retaining their textures' cutout silhouettes. */
@RequiredArgsConstructor
final class VisionEntityMasks implements MultiBufferSource, AutoCloseable {

    private static final int THERMAL_PASS = 1;
    private static final int SONAR_PASS = 2;
    private static int pass;
    private final RenderTarget target;
    private final boolean thermal;
    private final Reference2ObjectOpenHashMap<RenderType, MaskBatch> batches = new Reference2ObjectOpenHashMap<>();
    private final ReferenceLinkedOpenHashSet<MaskBatch> pending = new ReferenceLinkedOpenHashSet<>();
    private @Nullable VisionEntityMasks companion;
    private int color;

    static boolean rendering() {
        return pass != 0;
    }

    static boolean sonarRendering() {
        return (pass & SONAR_PASS) != 0;
    }

    void draw(RenderLevelStageEvent event, Minecraft client, ClientLevel level, Vec3 player) {
        draw(event, client, level, player, null);
    }

    void draw(RenderLevelStageEvent event, Minecraft client, ClientLevel level, Vec3 player, @Nullable VisionEntityMasks companion) {
        Vec3 camera = event.getCamera().getPosition();
        var poses = new PoseStack();
        var dispatcher = client.getEntityRenderDispatcher();
        this.companion = companion;
        pass = (thermal ? THERMAL_PASS : 0) | (!thermal || companion != null ? SONAR_PASS : 0);
        try {
            for (Entity entity : level.entitiesForRendering()) {
                if (entity == client.getCameraEntity() && client.options.getCameraType().isFirstPerson()) continue;
                boolean sonarVisible = false;
                if (!thermal || companion != null) {
                    sonarVisible = entity.getBoundingBox().distanceToSqr(player) <= VisionSectionCache.SONAR_RADIUS_SQUARED;
                    if (!sonarVisible && !thermal) continue;
                }
                if (!event.getFrustum().isVisible(entity.getBoundingBoxForCulling())) continue;
                int temperature = thermal ? ThermalSources.temperature(entity) : 0;
                color = thermal ? 0xff000000 | temperature << 16 : 0xffffffff;
                float partial = level.tickRateManager().isEntityFrozen(entity) ? 1 : event.getPartialTick().getGameTimeDeltaPartialTick(false);
                var renderer = dispatcher.getRenderer(entity);
                Vec3 position = entity.getPosition(partial).subtract(camera).add(renderer.getRenderOffset(entity, partial));
                poses.pushPose();
                poses.translate(position.x, position.y, position.z);
                float yaw = Mth.lerp(partial, entity.yRotO, entity.getYRot());
                if (companion != null && !sonarVisible) {
                    this.companion = null;
                    pass = THERMAL_PASS;
                    renderer.render(entity, yaw, partial, poses, this, LightTexture.FULL_BRIGHT);
                    this.companion = companion;
                    pass = THERMAL_PASS | SONAR_PASS;
                } else if (companion != null && entity instanceof LivingEntity living && living.isInvisible()) {
                    // Sonar forces an invisible living body visible; keep that extra geometry out of the heat mask.
                    this.companion = null;
                    pass = THERMAL_PASS;
                    renderer.render(entity, yaw, partial, poses, this, LightTexture.FULL_BRIGHT);
                    companion.color = 0xffffffff;
                    pass = SONAR_PASS;
                    renderer.render(entity, yaw, partial, poses, companion, LightTexture.FULL_BRIGHT);
                    this.companion = companion;
                    pass = THERMAL_PASS | SONAR_PASS;
                } else renderer.render(entity, yaw, partial, poses, this, LightTexture.FULL_BRIGHT);
                poses.popPose();
            }
            // Item renderers retain base and glint consumers at the same time. Switching layers must not
            // finish either builder before VertexMultiConsumer has submitted both copies of the model.
            drawPending(camera, player);
            if (companion != null) companion.drawPending(camera, player);
        } finally {
            pass = 0;
            this.companion = null;
        }
    }

    @Override
    @SuppressWarnings("resource") // Batches live for the framebuffer lifetime and close together in close().
    public VertexConsumer getBuffer(RenderType original) {
        boolean textured = original.format().contains(VertexFormatElement.UV0);
        MaskBatch batch = batch(original);
        VertexConsumer consumer = new MaskColor(batch.builder, color, textured);
        if (companion == null) return consumer;
        MaskBatch sonarBatch = companion.batch(original);
        VertexConsumer sonar = new MaskColor(sonarBatch.builder, companion.color, textured);
        return VertexMultiConsumer.create(consumer, sonar);
    }

    private MaskBatch batch(RenderType original) {
        MaskBatch batch = batches.computeIfAbsent(original, (RenderType type) -> new MaskBatch(maskType(type)));
        pending.add(batch);
        return batch;
    }

    private void drawPending(Vec3 camera, Vec3 player) {
        var shader = DEShaders.getVisionEntity();
        shader.safeGetUniform("CameraOffset").set((float) (camera.x - player.x), (float) (camera.y - player.y), (float) (camera.z - player.z));
        shader.safeGetUniform("ScanRadiusSquared").set(thermal ? 0f : (float) VisionSectionCache.SONAR_RADIUS_SQUARED);
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
    }

    private static final class MaskBatch implements AutoCloseable {

        private final RenderType type;
        private final ByteBufferBuilder bytes = new ByteBufferBuilder(32768);
        private BufferBuilder builder;

        private MaskBatch(RenderType type) {
            this.type = type;
            builder = new BufferBuilder(bytes, type.mode(), type.format());
        }

        private void draw() {
            var mesh = builder.build();
            if (mesh != null) type.draw(mesh);
            builder = new BufferBuilder(bytes, type.mode(), type.format());
        }

        @Override
        public void close() {
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
