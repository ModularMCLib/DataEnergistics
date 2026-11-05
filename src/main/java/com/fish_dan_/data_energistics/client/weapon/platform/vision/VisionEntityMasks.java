package com.fish_dan_.data_energistics.client.weapon.platform.vision;

import com.fish_dan_.data_energistics.client.registry.DEShaders;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
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
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceLinkedOpenHashSet;
import lombok.RequiredArgsConstructor;
import org.lwjgl.opengl.GL11;

/** Renders the actual posed models into an isolated mask, retaining their textures' cutout silhouettes. */
@RequiredArgsConstructor
final class VisionEntityMasks implements MultiBufferSource, AutoCloseable {

    private static int pass;
    private final RenderTarget target;
    private final boolean thermal;
    private final Reference2ObjectOpenHashMap<RenderType, MaskBatch> batches = new Reference2ObjectOpenHashMap<>();
    private final ReferenceLinkedOpenHashSet<MaskBatch> pending = new ReferenceLinkedOpenHashSet<>();
    private int color;

    static boolean rendering() {
        return pass != 0;
    }

    static boolean sonarRendering() {
        return pass == 2;
    }

    void draw(RenderLevelStageEvent event, Minecraft client, ClientLevel level, Vec3 player) {
        Vec3 camera = event.getCamera().getPosition();
        var shader = DEShaders.getVisionEntity();
        shader.safeGetUniform("CameraOffset").set((float) (camera.x - player.x), (float) (camera.y - player.y), (float) (camera.z - player.z));
        shader.safeGetUniform("ScanRadiusSquared").set(thermal ? 0f : (float) VisionSectionCache.SONAR_RADIUS_SQUARED);
        var poses = new PoseStack();
        var dispatcher = client.getEntityRenderDispatcher();
        pass = thermal ? 1 : 2;
        try {
            for (Entity entity : level.entitiesForRendering()) {
                if (entity == client.getCameraEntity() && client.options.getCameraType().isFirstPerson()) continue;
                int temperature = thermal ? ThermalSources.temperature(entity) : 255;
                if (temperature == 0 || !event.getFrustum().isVisible(entity.getBoundingBoxForCulling())) continue;
                if (!thermal && entity.getBoundingBox().distanceToSqr(player) > VisionSectionCache.SONAR_RADIUS_SQUARED) continue;
                color = 0xff000000 | temperature << 16;
                float partial = level.tickRateManager().isEntityFrozen(entity) ? 1 : event.getPartialTick().getGameTimeDeltaPartialTick(false);
                var renderer = dispatcher.getRenderer(entity);
                Vec3 position = entity.getPosition(partial).subtract(camera).add(renderer.getRenderOffset(entity, partial));
                poses.pushPose();
                poses.translate(position.x, position.y, position.z);
                renderer.render(entity, Mth.lerp(partial, entity.yRotO, entity.getYRot()), partial, poses, this, LightTexture.FULL_BRIGHT);
                poses.popPose();
            }
            // Item renderers retain base and glint consumers at the same time. Switching layers must not
            // finish either builder before VertexMultiConsumer has submitted both copies of the model.
            for (var batch : pending) batch.draw();
            pending.clear();
        } finally {
            pass = 0;
        }
    }

    @Override
    public VertexConsumer getBuffer(RenderType original) {
        MaskBatch batch = batches.computeIfAbsent(original, (RenderType type) -> new MaskBatch(maskType(type)));
        pending.add(batch);
        return new MaskColor(batch.builder, color, original.format().contains(VertexFormatElement.UV0));
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
