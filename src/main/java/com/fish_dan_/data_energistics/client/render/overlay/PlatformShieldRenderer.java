package com.fish_dan_.data_energistics.client.render.overlay;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.client.weapon.platform.PlatformClientState;
import com.fish_dan_.data_energistics.common.weapon.platform.protocol.ShieldVisualSnapshot;
import com.fish_dan_.data_energistics.common.weapon.platform.shield.ShieldAppearance;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Arrays;
import java.util.Comparator;
import java.util.OptionalDouble;

/** Truncated icosahedron: twenty hexagons and twelve pentagons, with inset faces and independent edges. */
@EventBusSubscriber(modid = Data_Energistics.MODID, value = Dist.CLIENT)
public final class PlatformShieldRenderer {

    private static final double RADIUS = 1.2;
    private static final Vector3f ROTATION_AXIS = new Vector3f(1, 1, 0.35f).normalize();
    private static final RenderType FACES = RenderType.create("platform_shield_faces", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 4096, false, true,
            RenderType.CompositeState.builder().setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY).setCullState(RenderStateShard.NO_CULL)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE).createCompositeState(false));
    private static final RenderType[] OUTLINES = new RenderType[4];
    private static final ObjectList<Panel> PANELS = geometry();

    static {
        for (int width = 1; width <= OUTLINES.length; width++) {
            OUTLINES[width - 1] = RenderType.create("platform_shield_outline_" + width, DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.LINES, 4096,
                    RenderType.CompositeState.builder().setShaderState(RenderStateShard.RENDERTYPE_LINES_SHADER)
                            .setLineState(new RenderStateShard.LineStateShard(OptionalDouble.of(width)))
                            .setLayeringState(RenderStateShard.VIEW_OFFSET_Z_LAYERING).setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                            .setOutputState(RenderStateShard.ITEM_ENTITY_TARGET).setWriteMaskState(RenderStateShard.COLOR_DEPTH_WRITE)
                            .setCullState(RenderStateShard.NO_CULL).createCompositeState(false));
        }
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return;
        var buffers = client.renderBuffers().bufferSource();
        var poses = event.getPoseStack();
        var camera = event.getCamera().getPosition();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        boolean firstPerson = client.options.getCameraType().isFirstPerson();
        double now = client.level.getGameTime() + partial;
        var rotation = new Quaternionf().rotationAxis((float) ((now % 1200) * Math.PI / 600), ROTATION_AXIS);
        var inverse = new Quaternionf(rotation).conjugate();
        var shields = new ObjectArrayList<ShieldFrame>();
        PlatformClientState.shields.values().removeIf(snapshot -> !(client.level.getEntity(snapshot.entity()) instanceof Player player) || !player.getUUID().equals(snapshot.player()));
        for (var player : client.level.players()) {
            if (player == client.player && firstPerson) continue;
            ShieldVisualSnapshot snapshot = PlatformClientState.shields.get(player.getId());
            if (snapshot == null || !snapshot.player().equals(player.getUUID()) || !snapshot.state().active()) continue;
            var settings = snapshot.appearance();
            if (settings.display() == ShieldAppearance.Display.HIDDEN) continue;
            float[] opacity = new float[PANELS.size()];
            if (settings.display() == ShieldAppearance.Display.ALWAYS) Arrays.fill(opacity, 1);
            else for (var impact : snapshot.impacts()) {
                double age = now - impact.at();
                if (age < 0 || age >= 30) continue;
                float fade = (float) Math.min(1, (30 - age) / 20);
                var direction = inverse.transform(new Vector3f((float) impact.direction().x, (float) impact.direction().y, (float) impact.direction().z));
                for (int index = 0; index < PANELS.size(); index++) {
                    Vec3 normal = PANELS.get(index).normal();
                    double alignment = normal.x * direction.x + normal.y * direction.y + normal.z * direction.z;
                    float local = impact.direction().equals(Vec3.ZERO) ? 1 : (float) Math.clamp((alignment - 0.55) / 0.25, 0, 1);
                    opacity[index] = Math.max(opacity[index], fade * local);
                }
            }
            Vec3 origin = player.getPosition(partial).add(0, player.getBbHeight() / 2, 0).subtract(camera);
            shields.add(new ShieldFrame(origin, settings, opacity));
        }
        // Draw faces together, then batch the independent outlines by their cached line width.
        for (int pass = 0; pass <= OUTLINES.length; pass++) {
            RenderType type = pass == 0 ? FACES : OUTLINES[pass - 1];
            for (var shield : shields) {
                if (pass != 0 && shield.settings().borderWidth() != pass) continue;
                int color = pass == 0 ? shield.settings().color() : shield.settings().borderColor();
                if (color >>> 24 == 0) continue;
                poses.pushPose();
                poses.translate(shield.origin().x, shield.origin().y, shield.origin().z);
                poses.mulPose(rotation);
                var pose = poses.last();
                var vertices = buffers.getBuffer(type);
                for (int index = 0; index < PANELS.size(); index++) {
                    int alpha = Math.round((color >>> 24) * shield.opacity()[index]);
                    if (alpha == 0) continue;
                    int panelColor = color & 0xffffff | alpha << 24;
                    Panel panel = PANELS.get(index);
                    if (pass == 0) {
                        for (Vec3 point : panel.fill()) vertices.addVertex(pose, (float) point.x, (float) point.y, (float) point.z).setColor(panelColor);
                    } else for (Edge edge : panel.outline()) {
                        Vec3 a = edge.a(), b = edge.b(), normal = edge.normal();
                        vertices.addVertex(pose, (float) a.x, (float) a.y, (float) a.z).setColor(panelColor).setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
                        vertices.addVertex(pose, (float) b.x, (float) b.y, (float) b.z).setColor(panelColor).setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
                    }
                }
                poses.popPose();
            }
            buffers.endBatch(type);
        }
    }

    private static ObjectList<Panel> geometry() {
        double phi = (1 + Math.sqrt(5)) / 2;
        var vertices = new ObjectArrayList<Vec3>();
        for (int a : new int[] { -1, 1 }) for (int b : new int[] { -1, 1 }) {
            vertices.add(new Vec3(0, a, b * phi));
            vertices.add(new Vec3(a, b * phi, 0));
            vertices.add(new Vec3(b * phi, 0, a));
        }
        var faces = new ObjectArrayList<ObjectList<Vec3>>();
        for (int a = 0; a < vertices.size(); a++) {
            Vec3 source = vertices.get(a);
            var pentagon = new ObjectArrayList<Vec3>();
            for (Vec3 vertex : vertices) {
                if (Math.abs(source.distanceToSqr(vertex) - 4) < 1e-6) pentagon.add(source.scale(2).add(vertex).normalize().scale(RADIUS));
            }
            order(pentagon);
            faces.add(pentagon);
            for (int b = a + 1; b < vertices.size(); b++) for (int c = b + 1; c < vertices.size(); c++) {
                Vec3 second = vertices.get(b), third = vertices.get(c);
                if (Math.abs(source.distanceToSqr(second) - 4) > 1e-6 || Math.abs(source.distanceToSqr(third) - 4) > 1e-6 || Math.abs(second.distanceToSqr(third) - 4) > 1e-6) continue;
                var hexagon = new ObjectArrayList<Vec3>();
                for (Vec3[] edge : new Vec3[][] { { source, second }, { second, third }, { third, source } }) {
                    hexagon.add(edge[0].scale(2).add(edge[1]).normalize().scale(RADIUS));
                    hexagon.add(edge[1].scale(2).add(edge[0]).normalize().scale(RADIUS));
                }
                order(hexagon);
                faces.add(hexagon);
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
                Vec3 a = polygon.get(index).lerp(center, 0.16), b = polygon.get((index + 1) % polygon.size()).lerp(center, 0.16);
                fill.add(center);
                fill.add(a);
                fill.add(b);
                fill.add(center);
                edges.add(new Edge(a, b, b.subtract(a).normalize()));
            }
            panels.add(new Panel(fill, edges, center.normalize()));
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

    private record ShieldFrame(Vec3 origin, ShieldAppearance settings, float[] opacity) {}

    private record Panel(ObjectList<Vec3> fill, ObjectList<Edge> outline, Vec3 normal) {}

    private record Edge(Vec3 a, Vec3 b, Vec3 normal) {}
}
