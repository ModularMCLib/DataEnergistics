package com.fish_dan_.data_energistics.client.render.overlay;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.client.hud.weapon_platform.preferences.HudModule;
import com.fish_dan_.data_energistics.client.hud.weapon_platform.preferences.TacticalHudPreferences;
import com.fish_dan_.data_energistics.client.weapon.platform.PlatformClientState;
import com.fish_dan_.data_energistics.common.weapon.platform.model.PlatformAbility;
import com.fish_dan_.data_energistics.common.weapon.platform.prospecting.OreSection;
import com.fish_dan_.data_energistics.common.weapon.platform.prospecting.OreSurveyDelta;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.model.data.ModelData;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

/** Section meshes are uploaded only when a scan changes. A single depth-free pass draws every visible section. */
@EventBusSubscriber(modid = Data_Energistics.MODID, value = Dist.CLIENT)
public final class PlatformOreRenderer {

    private static final Long2ObjectOpenHashMap<Mesh> MESHES = new Long2ObjectOpenHashMap<>();
    private static final Long2IntOpenHashMap ORES = new Long2IntOpenHashMap();
    private static final Int2IntOpenHashMap COLORS = new Int2IntOpenHashMap();
    private static final RenderType OUTLINES = RenderType.create("platform_ore_outlines", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.DEBUG_LINES, 4096, false, false,
            RenderType.CompositeState.builder().setShaderState(RenderStateShard.POSITION_COLOR_SHADER).setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(RenderStateShard.NO_DEPTH_TEST).setCullState(RenderStateShard.NO_CULL).setWriteMaskState(RenderStateShard.COLOR_WRITE).createCompositeState(false));
    public static @Nullable BlockPos aimed;
    private static final int[][] EDGES = { { 0, 1 }, { 0, 2 }, { 0, 4 }, { 1, 3 }, { 1, 5 }, { 2, 3 }, { 2, 6 }, { 3, 7 }, { 4, 5 }, { 4, 6 }, { 5, 7 }, { 6, 7 } };

    public static void accept(OreSurveyDelta delta) {
        if (delta.reset()) clear();
        for (long section : delta.removed()) remove(section);
        for (OreSection section : delta.sections()) {
            remove(section.section());
            if (section.positions().isEmpty()) continue;
            BlockPos origin = SectionPos.of(section.section()).origin();
            var vertexBuffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
            try (var bytes = new ByteBufferBuilder(4096)) {
                var builder = new BufferBuilder(bytes, VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
                for (int index = 0; index < section.positions().size(); index++) {
                    int position = section.positions().getInt(index), state = section.states().getInt(index);
                    int x = position & 15, z = position >> 4 & 15, y = position >> 8 & 15;
                    ORES.put(origin.offset(x, y, z).asLong(), state);
                    int color = color(state);
                    for (int[] edge : EDGES) for (int corner : edge) builder.addVertex(x + (corner & 1), y + (corner >> 1 & 1), z + (corner >> 2 & 1)).setColor(0xff000000 | color);
                }
                try (var mesh = builder.buildOrThrow()) {
                    vertexBuffer.bind();
                    vertexBuffer.upload(mesh);
                }
                VertexBuffer.unbind();
            }
            MESHES.put(section.section(), new Mesh(section, vertexBuffer, origin));
        }
    }

    public static void clear() {
        for (Mesh mesh : MESHES.values()) mesh.buffer().close();
        MESHES.clear();
        ORES.clear();
        COLORS.clear();
        aimed = null;
    }

    private static void remove(long section) {
        Mesh mesh = MESHES.remove(section);
        if (mesh == null) return;
        mesh.buffer().close();
        for (int position : mesh.section().positions()) ORES.remove(mesh.origin().offset(position & 15, position >> 8 & 15, position >> 4 & 15).asLong());
    }

    private static int color(int stateId) {
        if (COLORS.containsKey(stateId)) return COLORS.get(stateId);
        var state = Block.stateById(stateId);
        var sprite = Minecraft.getInstance().getBlockRenderer().getBlockModel(state).getParticleIcon(ModelData.EMPTY);
        double r = 0, g = 0, b = 0, total = 0;
        for (int x = 0; x < sprite.contents().width(); x++) for (int y = 0; y < sprite.contents().height(); y++) {
            int pixel = sprite.getPixelRGBA(0, x, y), red = pixel & 255, green = pixel >> 8 & 255, blue = pixel >> 16 & 255;
            double weight = (pixel >>> 24) * (1 + Math.pow(Math.max(red, Math.max(green, blue)) - Math.min(red, Math.min(green, blue)), 2));
            total += weight;
            r += red * weight;
            g += green * weight;
            b += blue * weight;
        }
        int color = total == 0 ? state.getMapColor(Minecraft.getInstance().level, BlockPos.ZERO).col : (int) (r / total) << 16 | (int) (g / total) << 8 | (int) (b / total);
        COLORS.put(stateId, color);
        return color;
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) {
            if (!MESHES.isEmpty()) clear();
            return;
        }
        if (!PlatformClientState.snapshot.present()) {
            if (!MESHES.isEmpty()) clear();
            return;
        }
        if ((PlatformClientState.snapshot.systems().active() & PlatformAbility.PROSPECTING.flag) == 0 || !TacticalHudPreferences.get(HudModule.ORES).visible()) {
            aimed = null;
            return;
        }
        OUTLINES.setupRenderState();
        try {
            Vec3 camera = event.getCamera().getPosition();
            for (Mesh mesh : MESHES.values()) {
                if (!event.getFrustum().isVisible(new AABB(mesh.origin()).expandTowards(16, 16, 16))) continue;
                mesh.buffer().bind();
                mesh.buffer().drawWithShader(new Matrix4f(event.getModelViewMatrix()).translate((float) (mesh.origin().getX() - camera.x), (float) (mesh.origin().getY() - camera.y), (float) (mesh.origin().getZ() - camera.z)), event.getProjectionMatrix(), GameRenderer.getPositionColorShader());
            }
        } finally {
            VertexBuffer.unbind();
            OUTLINES.clearRenderState();
        }
        aimed = null;
        Vec3 from = client.player.getEyePosition(), direction = client.player.getViewVector(1);
        int radius = PlatformClientState.snapshot.systems().settings().scanRadius();
        for (double distance = 0; distance <= radius; distance += 0.5) {
            BlockPos pos = BlockPos.containing(from.add(direction.scale(distance)));
            if (ORES.containsKey(pos.asLong())) {
                aimed = pos;
                break;
            }
        }
    }

    public static Component label() {
        Minecraft client = Minecraft.getInstance();
        if (aimed == null || client.player == null) return Component.empty();
        return Component.translatable("hud.data_energistics.weapon_platform.ore", Block.stateById(ORES.get(aimed.asLong())).getBlock().getName(), String.format(Locale.ROOT, "%.1f", client.player.getEyePosition().distanceTo(Vec3.atCenterOf(aimed))));
    }

    private record Mesh(OreSection section, VertexBuffer buffer, BlockPos origin) {}
}
