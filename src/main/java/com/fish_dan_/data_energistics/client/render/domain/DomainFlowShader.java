package com.fish_dan_.data_energistics.client.render.domain;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.client.renderer.ShaderInstance;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.Objects;

/** Moves cached boundary paths and fades their yellow tails without rebuilding geometry each frame. */
public final class DomainFlowShader {

    private static @Nullable ShaderInstance shader;

    private DomainFlowShader() {}

    public static void register(RegisterShadersEvent event) throws IOException {
        event.registerShader(new ShaderInstance(event.getResourceProvider(), Data_Energistics.id("domain_flow"), DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL), loaded -> shader = loaded);
    }

    public static ShaderInstance get() {
        return Objects.requireNonNull(shader, "Domain flow shader has not loaded");
    }
}
