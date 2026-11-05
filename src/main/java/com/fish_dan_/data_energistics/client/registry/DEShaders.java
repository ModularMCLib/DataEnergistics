package com.fish_dan_.data_energistics.client.registry;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.client.renderer.ShaderInstance;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import lombok.Getter;

import java.io.IOException;

/** Shader instances are assigned by the registration event before world rendering and replaced on resource reload. */
public final class DEShaders {

    @Getter
    private static ShaderInstance domainFlow;
    @Getter
    private static ShaderInstance oreOutline;
    @Getter
    private static ShaderInstance visionSurface;
    @Getter
    private static ShaderInstance visionEntity;
    @Getter
    private static ShaderInstance visionComposite;

    private DEShaders() {}

    public static void register(RegisterShadersEvent event) throws IOException {
        event.registerShader(new ShaderInstance(event.getResourceProvider(), Data_Energistics.id("domain_flow"), DefaultVertexFormat.POSITION_TEX_COLOR), shader -> domainFlow = shader);
        event.registerShader(new ShaderInstance(event.getResourceProvider(), Data_Energistics.id("ore_outline"), DefaultVertexFormat.POSITION_COLOR), shader -> oreOutline = shader);
        event.registerShader(new ShaderInstance(event.getResourceProvider(), Data_Energistics.id("vision_surface"), DefaultVertexFormat.POSITION_COLOR), shader -> visionSurface = shader);
        event.registerShader(new ShaderInstance(event.getResourceProvider(), Data_Energistics.id("vision_entity"), DefaultVertexFormat.POSITION_TEX_COLOR), shader -> visionEntity = shader);
        event.registerShader(new ShaderInstance(event.getResourceProvider(), Data_Energistics.id("vision_composite"), DefaultVertexFormat.BLIT_SCREEN), shader -> visionComposite = shader);
    }
}
