package com.fish_dan_.data_energistics.client.render.overlay;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.client.weapon.platform.DomainClientState;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@EventBusSubscriber(modid = Data_Energistics.MODID, value = Dist.CLIENT)
public final class PlatformDomainRenderer {

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        var client = Minecraft.getInstance();
        if (client.level == null) {
            DomainClientState.clear();
            return;
        }
        var poses = event.getPoseStack();
        var camera = event.getCamera().getPosition();
        var buffers = client.renderBuffers().bufferSource();
        var lines = buffers.getBuffer(RenderType.lines());
        poses.pushPose();
        poses.translate(-camera.x, -camera.y, -camera.z);
        for (var domain : DomainClientState.domains) {
            if (domain.dimension() != client.level.dimension()) continue;
            int half = domain.size() / 2;
            var box = new AABB((domain.x() - half) * 16, client.level.getMinBuildHeight(), (domain.z() - half) * 16, (domain.x() + half + 1) * 16, client.level.getMaxBuildHeight(), (domain.z() + half + 1) * 16);
            LevelRenderer.renderLineBox(poses, lines, box, 0.65f, 0.7f, 0.9f, 0.8f);
            for (double y = client.level.getMinBuildHeight(); y < client.level.getMaxBuildHeight(); y += 16) LevelRenderer.renderLineBox(poses, lines, new AABB(box.minX, y, box.minZ, box.maxX, y, box.maxZ), 0.65f, 0.7f, 0.9f, 0.3f);
        }
        poses.popPose();
        buffers.endBatch(RenderType.lines());
    }
}
