package com.fish_dan_.data_energistics.client.render.overlay;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.client.renderer.ShaderInstance;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;

import java.io.IOException;

/** Clips cached section geometry at the exact horizontal scan radius without rebuilding it as the wearer moves. */
public final class OreOutlineShader {

    private static ShaderInstance shader;

    private OreOutlineShader() {}

    public static void register(RegisterShadersEvent event) throws IOException {
        event.registerShader(new ShaderInstance(event.getResourceProvider(), Data_Energistics.id("ore_outline"), DefaultVertexFormat.POSITION_COLOR), loaded -> shader = loaded);
    }

    static ShaderInstance get() {
        return shader;
    }
}
