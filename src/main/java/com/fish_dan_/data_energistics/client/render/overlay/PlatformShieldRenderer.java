package com.fish_dan_.data_energistics.client.render.overlay;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.client.weapon.platform.PlatformClientState;
import com.fish_dan_.data_energistics.common.data.DEDataComponents;
import com.fish_dan_.data_energistics.common.weapon.platform.equipment.EquippedWeaponPlatform;
import com.fish_dan_.data_energistics.common.weapon.platform.shield.ShieldState;
import com.fish_dan_.data_energistics.common.weapon.platform.storage.PlatformAbilitySettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import java.util.Comparator;

/** Truncated icosahedron: twenty hexagons and twelve pentagons, with inset faces and independent edges. */
@EventBusSubscriber(modid = Data_Energistics.MODID, value = Dist.CLIENT)
public final class PlatformShieldRenderer {
    private static final RenderType FACES = RenderType.create("platform_shield_faces", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 4096, false, true,
            RenderType.CompositeState.builder().setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY).setCullState(RenderStateShard.NO_CULL)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE).createCompositeState(false));
    private static final ObjectList<Panel> PANELS = geometry();

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return;
        var buffers = client.renderBuffers().bufferSource();
        var poses = event.getPoseStack();
        var camera = event.getCamera().getPosition();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        for (var player : client.level.players()) {
            var platform = EquippedWeaponPlatform.find(player);
            var state = player == client.player ? PlatformClientState.snapshot.systems().shield() : platform.getOrDefault(DEDataComponents.PLATFORM_SHIELD, ShieldState.EMPTY);
            if (!state.active() || state.charge() == 0) continue;
            int color = player == client.player ? PlatformClientState.snapshot.systems().settings().shieldColor() : platform.getOrDefault(DEDataComponents.PLATFORM_ABILITIES, PlatformAbilitySettings.DEFAULT).shieldColor();
            Vec3 origin = player.getPosition(partial).add(0, player.getBbHeight() / 2, 0).subtract(camera);
            poses.pushPose();
            poses.translate(origin.x, origin.y, origin.z);
            var pose = poses.last();
            var fill = buffers.getBuffer(FACES);
            for (Panel panel : PANELS) for (Vec3 point : panel.fill()) fill.addVertex(pose, (float) point.x, (float) point.y, (float) point.z).setColor(color & 0xffffff | Math.min(80, color >>> 24) << 24);
            var lines = buffers.getBuffer(RenderType.lines());
            for (Panel panel : PANELS) for (Edge edge : panel.outline()) {
                Vec3 a = edge.a(), b = edge.b(), normal = edge.normal();
                lines.addVertex(pose, (float) a.x, (float) a.y, (float) a.z).setColor(color).setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
                lines.addVertex(pose, (float) b.x, (float) b.y, (float) b.z).setColor(color).setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
            }
            poses.popPose();
        }
        buffers.endBatch(FACES);
        buffers.endBatch(RenderType.lines());
    }

    private static ObjectList<Panel> geometry() {
        double phi = (1 + Math.sqrt(5)) / 2;
        var vertices = new ObjectArrayList<Vec3>();
        for (int a : new int[] { -1, 1 }) for (int b : new int[] { -1, 1 }) {
            vertices.add(new Vec3(0, a, b * phi)); vertices.add(new Vec3(a, b * phi, 0)); vertices.add(new Vec3(b * phi, 0, a));
        }
        var faces = new ObjectArrayList<ObjectList<Vec3>>();
        for (int a = 0; a < vertices.size(); a++) {
            Vec3 source = vertices.get(a);
            var pentagon = new ObjectArrayList<Vec3>();
            for (int b = 0; b < vertices.size(); b++) if (Math.abs(source.distanceToSqr(vertices.get(b)) - 4) < 1e-6) pentagon.add(source.scale(2).add(vertices.get(b)).normalize().scale(1.5));
            order(pentagon); faces.add(pentagon);
            for (int b = a + 1; b < vertices.size(); b++) for (int c = b + 1; c < vertices.size(); c++) {
                Vec3 second = vertices.get(b), third = vertices.get(c);
                if (Math.abs(source.distanceToSqr(second) - 4) > 1e-6 || Math.abs(source.distanceToSqr(third) - 4) > 1e-6 || Math.abs(second.distanceToSqr(third) - 4) > 1e-6) continue;
                var hexagon = new ObjectArrayList<Vec3>();
                for (Vec3[] edge : new Vec3[][] { { source, second }, { second, third }, { third, source } }) {
                    hexagon.add(edge[0].scale(2).add(edge[1]).normalize().scale(1.5));
                    hexagon.add(edge[1].scale(2).add(edge[0]).normalize().scale(1.5));
                }
                order(hexagon); faces.add(hexagon);
            }
        }
        var panels = new ObjectArrayList<Panel>();
        for (var polygon : faces) {
            Vec3 sum = Vec3.ZERO;
            for (Vec3 point : polygon) sum = sum.add(point);
            Vec3 center = sum.scale(1.0 / polygon.size());
            var fill = new ObjectArrayList<Vec3>();
            var edges = new ObjectArrayList<Edge>();
            for (int index = 0; index < polygon.size(); index++) {
                Vec3 a = polygon.get(index).lerp(center, 0.05), b = polygon.get((index + 1) % polygon.size()).lerp(center, 0.05);
                fill.add(center); fill.add(a); fill.add(b); fill.add(center);
                edges.add(new Edge(a, b, b.subtract(a).normalize()));
            }
            panels.add(new Panel(fill, edges));
        }
        return panels;
    }

    private static void order(ObjectList<Vec3> points) {
        Vec3 sum = Vec3.ZERO;
        for (Vec3 point : points) sum = sum.add(point);
        Vec3 center = sum.scale(1.0 / points.size()), normal = center.normalize();
        Vec3 horizontal = points.getFirst().subtract(center).normalize(), vertical = normal.cross(horizontal);
        points.sort(Comparator.comparingDouble(point -> Math.atan2(point.subtract(center).dot(vertical), point.subtract(center).dot(horizontal))));
    }
    private record Panel(ObjectList<Vec3> fill, ObjectList<Edge> outline) {}
    private record Edge(Vec3 a, Vec3 b, Vec3 normal) {}
}
