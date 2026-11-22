package com.fish_dan_.data_energistics.client.render.item.blade.effect;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.item.powered.LightSaberColorData;
import com.fish_dan_.data_energistics.common.item.powered.PoweredSwordItem;
import com.fish_dan_.data_energistics.common.item.powered.blade.BladeSwing;
import com.fish_dan_.data_energistics.common.item.powered.blade.BladeWeapon;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.FastColor.ARGB32;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.joml.Matrix4f;

/** A short-lived crescent with a bright cutting edge and a softer inner band. */
@EventBusSubscriber(modid = Data_Energistics.MOD_ID, value = Dist.CLIENT)
public final class BladeSlashRenderer {

    private static final int SEGMENTS = 32;
    private static final float[] BAND_DEPTH = { 1F, 0.52F, 0.12F, 0F };
    private static final float[] BAND_OPACITY = { 0F, 0.68F, 1F, 0F };
    private static final int EDGE_COLOR = 0xF7FFFE;
    private static final Shape FOREHAND = Shape.arc(-84, 69, -0.4, -0.10);
    private static final Shape BACKHAND = Shape.arc(-150, -283, 0.25, -0.30);
    private static final Shape DIAGONAL = Shape.arc(54, -102, 0.18, -0.11);
    private static final Shape HEAVY = Shape.arc(146, 313, -0.16, 0.20);
    private static final ObjectArrayList<Slash> SLASHES = new ObjectArrayList<>();
    private static final RenderType SLASH = RenderType.create("data_energistics_blade_slash",
            DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 16384, false, true,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setOutputState(RenderStateShard.PARTICLES_TARGET)
                    .createCompositeState(false));

    private BladeSlashRenderer() {}

    public static void start(Player player, BladeWeapon weapon, BladeSwing swing) {
        if (player.isInvisible()) return;
        ItemStack stack = player.getMainHandItem();
        boolean sanctifier = weapon == BladeWeapon.SANCTIFIER;
        if (!sanctifier && ((PoweredSwordItem) stack.getItem()).getAECurrentPower(stack) <= 0) return;

        Shape shape = switch (swing) {
            case FOREHAND -> FOREHAND;
            case BACKHAND -> BACKHAND;
            case DIAGONAL -> DIAGONAL;
            case HEAVY -> HEAVY;
        };
        boolean heavy = swing == BladeSwing.HEAVY;
        double radius = (sanctifier ? 1.15 : 1.0) * (heavy ? 1.28 : 1.0);
        double reach = radius + 0.83;
        Vec3 forward = player.getLookAngle();
        Vec3 right = Vec3.directionFromRotation(0, player.getYRot() + 90);
        Vec3 up = right.cross(forward);
        if (!sanctifier && player.getMainArm() == HumanoidArm.LEFT) right = right.scale(-1);
        Vec3 center = player.getEyePosition().add(forward.scale(reach)).add(right.scale(shape.centerX)).add(up.scale(shape.centerY));
        // Tilt the arc into the strike so its width is visible in first person as well as from the side.
        Vec3 across = right.add(forward.scale(0.44)).normalize();
        Vec3 normal = across.cross(up.add(forward.scale(-0.22))).normalize();
        Vec3 horizontal = across.scale(radius);
        Vec3 vertical = normal.cross(across).scale(radius);
        Matrix4f plane = new Matrix4f()
                .m00((float) horizontal.x).m01((float) horizontal.y).m02((float) horizontal.z)
                .m10((float) vertical.x).m11((float) vertical.y).m12((float) vertical.z)
                .m20((float) normal.x).m21((float) normal.y).m22((float) normal.z);
        int color = ARGB32.lerp(0.44F, LightSaberColorData.getBladeColor(stack), 0xFFFFFF);
        SLASHES.add(new Slash(player, player.tickCount, heavy ? 5.5F : 4.5F, heavy ? 0.27F : 0.20F, shape, center, plane, color));
    }

    public static void tick() {
        SLASHES.removeIf(slash -> !slash.player.isAlive() || slash.player.tickCount - slash.started >= slash.duration);
    }

    public static void clear() {
        SLASHES.clear();
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || SLASHES.isEmpty()) return;

        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 camera = event.getCamera().getPosition();
        PoseStack poses = event.getPoseStack();
        var buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        VertexConsumer vertices = buffers.getBuffer(SLASH);
        for (int index = 0; index < SLASHES.size(); index++) {
            Slash slash = SLASHES.get(index);
            float age = slash.player.tickCount - slash.started + partial;
            if (age >= slash.duration) continue;
            float opacity = 1 - Math.max(0, (age - 1.2F) / (slash.duration - 1.2F));
            poses.pushPose();
            poses.translate(slash.center.x - camera.x, slash.center.y - camera.y, slash.center.z - camera.z);
            poses.mulPose(slash.plane);
            emit(vertices, poses.last(), slash, age, opacity * opacity);
            poses.popPose();
        }
        buffers.endBatch(SLASH);
    }

    private static void emit(VertexConsumer vertices, PoseStack.Pose pose, Slash slash, float age, float opacity) {
        float reveal = Math.min(SEGMENTS, (0.38F + age * 0.52F) * SEGMENTS);
        int segments = Mth.ceil(reveal);
        for (int segment = 0; segment < segments; segment++) {
            float frontOpacity = Math.min(1, reveal - segment) * opacity;
            for (int band = 0; band < BAND_DEPTH.length - 1; band++) {
                vertex(vertices, pose, slash, segment, band, opacity);
                vertex(vertices, pose, slash, segment, band + 1, opacity);
                vertex(vertices, pose, slash, segment + 1, band + 1, frontOpacity);
                vertex(vertices, pose, slash, segment + 1, band, frontOpacity);
            }
        }
    }

    private static void vertex(VertexConsumer vertices, PoseStack.Pose pose, Slash slash, int segment, int band, float opacity) {
        Shape shape = slash.shape;
        float radius = 1 - shape.taper[segment] * slash.width * BAND_DEPTH[band];
        int rgb = band >= 2 ? EDGE_COLOR : slash.color;
        int color = ARGB32.color(Math.round(255 * opacity * BAND_OPACITY[band]), rgb);
        vertices.addVertex(pose, shape.x[segment] * radius, shape.y[segment] * radius, 0).setColor(color);
    }

    private record Slash(Player player, int started, float duration, float width, Shape shape, Vec3 center, Matrix4f plane, int color) {}

    private record Shape(float[] x, float[] y, float[] taper, double centerX, double centerY) {

        private static Shape arc(double start, double end, double centerX, double centerY) {
            float[] x = new float[SEGMENTS + 1];
            float[] y = new float[SEGMENTS + 1];
            float[] taper = new float[SEGMENTS + 1];
            for (int i = 0; i <= SEGMENTS; i++) {
                double fraction = i / (double) SEGMENTS;
                double angle = Math.toRadians(Mth.lerp(fraction, start, end));
                x[i] = (float) Math.cos(angle);
                y[i] = (float) Math.sin(angle);
                taper[i] = (float) Math.sqrt(Math.sin(Math.PI * fraction));
            }
            return new Shape(x, y, taper, centerX, centerY);
        }
    }
}
