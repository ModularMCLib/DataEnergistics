package com.fish_dan_.data_energistics.client.weapon.platform.vision;

import net.minecraft.client.Minecraft;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;

/** Owns all compositing attachments together, with the same size and depth/stencil format as the main target. */
final class VisionFramebuffers implements AutoCloseable {

    final TextureTarget scene;
    final TextureTarget heat;
    final TextureTarget sonar;
    final VisionEntityMasks heatEntities;
    final VisionEntityMasks sonarEntities;

    VisionFramebuffers(RenderTarget main) {
        scene = new TextureTarget(main.width, main.height, false, Minecraft.ON_OSX);
        heat = new TextureTarget(main.width, main.height, true, Minecraft.ON_OSX);
        sonar = new TextureTarget(main.width, main.height, false, Minecraft.ON_OSX);
        heat.setClearColor(0, 0, 0, 0);
        sonar.setClearColor(0, 0, 0, 0);
        if (main.isStencilEnabled()) heat.enableStencil();
        heatEntities = new VisionEntityMasks(heat, true);
        sonarEntities = new VisionEntityMasks(sonar, false);
    }

    boolean matches(RenderTarget main) {
        return scene.width == main.width && scene.height == main.height && heat.isStencilEnabled() == main.isStencilEnabled();
    }

    @Override
    public void close() {
        heatEntities.close();
        sonarEntities.close();
        scene.destroyBuffers();
        heat.destroyBuffers();
        sonar.destroyBuffers();
    }
}
