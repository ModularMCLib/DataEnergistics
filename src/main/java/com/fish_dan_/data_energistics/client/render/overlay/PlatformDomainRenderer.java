package com.fish_dan_.data_energistics.client.render.overlay;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.client.render.domain.DomainBoundaryMesh;
import com.fish_dan_.data_energistics.client.render.domain.DomainFlowShader;
import com.fish_dan_.data_energistics.client.weapon.platform.DomainClientState;
import com.fish_dan_.data_energistics.common.weapon.platform.domain.StasisDomain;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.joml.Matrix4f;

@EventBusSubscriber(modid = Data_Energistics.MODID, value = Dist.CLIENT)
public final class PlatformDomainRenderer {

    private static final Object2ObjectOpenHashMap<StasisDomain, DomainBoundaryMesh> MESHES = new Object2ObjectOpenHashMap<>();
    private static final RenderType FLOW = RenderType.create("platform_domain_flow", DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 65536, false, false,
            RenderType.CompositeState.builder().setShaderState(new RenderStateShard.ShaderStateShard(DomainFlowShader::get))
                    .setTransparencyState(RenderStateShard.ADDITIVE_TRANSPARENCY).setCullState(RenderStateShard.NO_CULL)
                    .setOutputState(RenderStateShard.ITEM_ENTITY_TARGET).setWriteMaskState(RenderStateShard.COLOR_WRITE).createCompositeState(false));

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        var client = Minecraft.getInstance();
        if (client.level == null) {
            DomainClientState.clear();
            return;
        }
        MESHES.object2ObjectEntrySet().removeIf(entry -> {
            if (entry.getKey().dimension() == client.level.dimension() && DomainClientState.domains.contains(entry.getKey())) return false;
            entry.getValue().close();
            return true;
        });
        if (DomainClientState.domains.isEmpty()) return;
        var camera = event.getCamera().getPosition();
        var shader = DomainFlowShader.get();
        shader.safeGetUniform("FlowTime").set((client.level.getGameTime() % 24000 + event.getPartialTick().getGameTimeDeltaPartialTick(false)) / 20);
        FLOW.setupRenderState();
        try {
            for (var domain : DomainClientState.domains) {
                if (domain.dimension() != client.level.dimension()) continue;
                var mesh = MESHES.get(domain);
                if (mesh != null && !mesh.current(client.level)) {
                    mesh.close();
                    MESHES.remove(domain);
                    mesh = null;
                }
                if (mesh == null) {
                    mesh = new DomainBoundaryMesh(client.level, domain);
                    MESHES.put(domain, mesh);
                }
                if (mesh.empty() || !event.getFrustum().isVisible(mesh.bounds())) continue;
                var origin = mesh.origin();
                mesh.vertices().bind();
                mesh.vertices().drawWithShader(new Matrix4f(event.getModelViewMatrix()).translate((float) (origin.x - camera.x), (float) (origin.y - camera.y), (float) (origin.z - camera.z)), event.getProjectionMatrix(), shader);
            }
        } finally {
            VertexBuffer.unbind();
            FLOW.clearRenderState();
        }
    }

    public static void clear() {
        if (!RenderSystem.isOnRenderThread()) {
            Minecraft.getInstance().execute(PlatformDomainRenderer::clear);
            return;
        }
        for (var mesh : MESHES.values()) mesh.close();
        MESHES.clear();
    }
}
