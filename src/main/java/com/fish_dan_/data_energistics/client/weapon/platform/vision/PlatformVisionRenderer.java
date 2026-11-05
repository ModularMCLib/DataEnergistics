package com.fish_dan_.data_energistics.client.weapon.platform.vision;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.client.registry.DEShaders;
import com.fish_dan_.data_energistics.client.weapon.platform.PlatformClientState;
import com.fish_dan_.data_energistics.common.weapon.platform.model.PlatformAbility;
import com.fish_dan_.data_energistics.util.client.vision.SonarPulse;
import com.fish_dan_.data_energistics.util.client.vision.ThermalSources;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;
import org.lwjgl.opengl.GL11;

/** Composites world-only heat and sonar, before the hand and GUI, without replacing Minecraft's post effect. */
@EventBusSubscriber(modid = Data_Energistics.MODID, value = Dist.CLIENT)
public final class PlatformVisionRenderer {

    private static @Nullable VisionFramebuffers targets;
    private static @Nullable ClientLevel world;
    private static final SonarPulse SONAR_PULSE = new SonarPulse();

    private PlatformVisionRenderer() {}

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) return;
        Minecraft client = Minecraft.getInstance();
        boolean thermal = PlatformVisionState.active(PlatformAbility.THERMAL_IMAGING), sonar = PlatformVisionState.active(PlatformAbility.SONAR);
        boolean lowLight = PlatformVisionState.active(PlatformAbility.LOW_LIGHT_VISION) && !PlatformVisionState.active(PlatformAbility.NIGHT_VISION);
        if (client.level == null || client.player == null || !thermal && !sonar && !lowLight) {
            clear();
            return;
        }
        var main = client.getMainRenderTarget();
        if (targets == null || world != client.level || !targets.matches(main)) {
            clear();
            targets = new VisionFramebuffers(main);
            world = client.level;
        }
        var frame = targets;
        VisionSectionCache.upload();
        var player = client.player.position();
        VisionSectionCache.prepareDraw(event, player, thermal, sonar);
        if (sonar) {
            SONAR_PULSE.update((double) client.level.getGameTime() + event.getPartialTick().getGameTimeDeltaPartialTick(false), player);
            SONAR_PULSE.apply(DEShaders.getVisionSurface(), player);
            SONAR_PULSE.apply(DEShaders.getVisionEntity(), player);
        } else SONAR_PULSE.clear();
        var modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.set(event.getModelViewMatrix());
        RenderSystem.applyModelViewMatrix();
        try {
            if (thermal) frame.heat.clear(Minecraft.ON_OSX);
            if (sonar) frame.sonar.clear(Minecraft.ON_OSX);
            if (thermal) {
                frame.heat.copyDepthFrom(main);
                frame.heat.bindWrite(false);
                RenderSystem.disableBlend();
                RenderSystem.disableCull();
                RenderSystem.enableDepthTest();
                RenderSystem.depthFunc(GL11.GL_LEQUAL);
                RenderSystem.depthMask(true);
                VisionSectionCache.draw(event, player, true);
                frame.heatEntities.draw(event, client, client.level, player, sonar ? frame.sonarEntities : null);
            }
            if (sonar && !thermal) frame.sonarEntities.draw(event, client, client.level, player);
            if (lowLight || thermal) frame.scene.copyDepthFrom(main);
            frame.scene.bindWrite(false);
            main.blitToScreen(main.width, main.height, true);
            main.bindWrite(false);
            RenderSystem.disableBlend();
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.colorMask(true, true, true, true);
            RenderSystem.setShader(DEShaders::getVisionComposite);
            var shader = DEShaders.getVisionComposite();
            shader.setSampler("SceneSampler", frame.scene.getColorTextureId());
            shader.setSampler("SceneDepthSampler", frame.scene.getDepthTextureId());
            shader.setSampler("HeatSampler", frame.heat.getColorTextureId());
            shader.setSampler("SonarSampler", frame.sonar.getColorTextureId());
            shader.safeGetUniform("PixelSize").set(1f / main.width, 1f / main.height);
            shader.safeGetUniform("ThermalEnabled").set(thermal ? 1f : 0f);
            shader.safeGetUniform("SonarEnabled").set(sonar ? 1f : 0f);
            var settings = PlatformClientState.snapshot.systems().settings();
            shader.safeGetUniform("Fusion").set(lowLight || PlatformVisionState.active(PlatformAbility.NIGHT_VISION) ? 1f : 0f);
            shader.safeGetUniform("Palette").set((float) settings.thermalPalette().ordinal());
            shader.safeGetUniform("AmbientTemperature").set(thermal ? ThermalSources.ambient(client.level, client.player.blockPosition()) / 255f : 0f);
            shader.safeGetUniform("LowLightEnabled").set(lowLight ? 1f : 0f);
            shader.safeGetUniform("Gain").set(settings.lowLight().gain());
            shader.safeGetUniform("AutomaticBrightness").set(settings.lowLight().automaticBrightness() ? 1f : 0f);
            shader.safeGetUniform("Noise").set(settings.lowLight().noise() / 100f);
            shader.safeGetUniform("InfraredEnabled").set(settings.lowLight().infraredIlluminator() ? 1f : 0f);
            shader.safeGetUniform("FrameTime").set(client.level.getGameTime() % 24000 + event.getPartialTick().getGameTimeDeltaPartialTick(false));
            shader.safeGetUniform("InverseProjection").set(new Matrix4f(event.getProjectionMatrix()).invert());
            var builder = RenderSystem.renderThreadTesselator().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.BLIT_SCREEN);
            builder.addVertex(0, 0, 0);
            builder.addVertex(1, 0, 0);
            builder.addVertex(1, 1, 0);
            builder.addVertex(0, 1, 0);
            BufferUploader.drawWithShader(builder.buildOrThrow());
            if (sonar) {
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                RenderSystem.disableDepthTest();
                RenderSystem.disableCull();
                VisionSectionCache.draw(event, player, false);
            }
        } finally {
            modelView.popMatrix();
            RenderSystem.applyModelViewMatrix();
            main.bindWrite(false);
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
            RenderSystem.setShaderColor(1, 1, 1, 1);
        }
    }

    public static boolean renderingEntities() {
        return VisionEntityMasks.rendering();
    }

    public static boolean sonarEntities() {
        return VisionEntityMasks.sonarRendering();
    }

    public static void clear() {
        if (targets != null) {
            targets.close();
            targets = null;
        }
        world = null;
        SONAR_PULSE.clear();
    }

    public static void reload() {
        clear();
        VisionSectionCache.clear();
    }
}
