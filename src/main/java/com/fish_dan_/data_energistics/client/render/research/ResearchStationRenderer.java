package com.fish_dan_.data_energistics.client.render.research;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.block.research.ResearchStationBlock;
import com.fish_dan_.data_energistics.blockentity.research.ResearchStationBlockEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.RenderTypeHelper;
import net.neoforged.neoforge.client.model.data.ModelData;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

/** Separately baked laser carriage, with a server-authoritative working beam and extended bounds. */
public final class ResearchStationRenderer implements BlockEntityRenderer<ResearchStationBlockEntity> {

    public static final ModelResourceLocation HEAD_OFF = ModelResourceLocation.standalone(Data_Energistics.id("block/research/laser_head_off"));
    public static final ModelResourceLocation HEAD_ON = ModelResourceLocation.standalone(Data_Energistics.id("block/research/laser_head_on"));
    private static final float HEAD_X = -15F / 16;
    private static final float HEAD_Y = 22F / 16;
    private static final float PIVOT_X = 7.09567F / 16;
    private static final float PIVOT_Y = 8.89403F / 16;
    private static final float EMITTER_X = 8F / 16;
    private static final float EMITTER_Y = 6F / 16;
    private static final float EMITTER_Z = 8F / 16;
    private static final float WORKBENCH_Y = 18.01F / 16;
    private static final float SWING_RADIANS = (float) Math.toRadians(10);
    private static final int SWING_PERIOD_TICKS = 80;
    private static final int BEAM_COLOR = 0x80A8FFE0;
    private final BlockRenderDispatcher renderer;

    public ResearchStationRenderer(BlockEntityRendererProvider.Context context) {
        this.renderer = context.getBlockRenderDispatcher();
    }

    @Override
    public AABB getRenderBoundingBox(ResearchStationBlockEntity host) {
        return new AABB(host.getBlockPos()).inflate(1).expandTowards(0, 1, 0);
    }

    @Override
    public void render(ResearchStationBlockEntity host, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        var level = host.getLevel();
        if (level == null) return;
        boolean working = host.working();
        float phase = (level.getGameTime() % SWING_PERIOD_TICKS + partialTick) * Mth.TWO_PI / SWING_PERIOD_TICKS;
        float swing = working ? Mth.sin(phase) * SWING_RADIANS : 0;
        pose.pushPose();
        try {
            pose.translate(0.5, 0, 0.5);
            float angle = switch (host.getBlockState().getValue(ResearchStationBlock.FACING)) {
                case NORTH -> 0;
                case EAST -> -90;
                case SOUTH -> -180;
                case WEST -> 90;
                default -> throw new IllegalStateException("Research facing must be horizontal");
            };
            pose.mulPose(Axis.YP.rotationDegrees(angle));
            pose.translate(-0.5, 0, -0.5);
            pose.pushPose();
            try {
                pose.translate(HEAD_X, HEAD_Y, 0);
                // Rotate around the supplied laser head's attachment pivot, keeping its nozzle and ray together.
                pose.translate(PIVOT_X, PIVOT_Y, EMITTER_Z);
                pose.mulPose(Axis.ZP.rotation(swing));
                pose.translate(-PIVOT_X, -PIVOT_Y, -EMITTER_Z);
                var model = Minecraft.getInstance().getModelManager().getModel(working ? HEAD_ON : HEAD_OFF);
                var state = host.getBlockState();
                for (RenderType layer : model.getRenderTypes(state, RandomSource.create(42), ModelData.EMPTY)) {
                    renderer.getModelRenderer().renderModel(pose.last(), buffers.getBuffer(RenderTypeHelper.getEntityRenderType(layer, false)),
                            state, model, 1, 1, 1, light, overlay, ModelData.EMPTY, layer);
                }
                if (working) {
                    float sine = Mth.sin(swing);
                    float cosine = Mth.cos(swing);
                    float nozzleY = HEAD_Y + PIVOT_Y + (EMITTER_X - PIVOT_X) * sine + (EMITTER_Y - PIVOT_Y) * cosine;
                    float length = (nozzleY - WORKBENCH_Y) / cosine;
                    beam(pose, buffers.getBuffer(RenderType.lightning()), length, sine / cosine);
                }
            } finally {
                pose.popPose();
            }
        } finally {
            pose.popPose();
        }
    }

    private static void beam(PoseStack pose, VertexConsumer vertices, float length, float slope) {
        var matrix = pose.last().pose();
        float width = 0.014f;
        for (int side = 0; side < 4; side++) {
            double first = side * Math.PI / 2;
            double second = (side + 1) * Math.PI / 2;
            float x1 = EMITTER_X + (float) Math.cos(first) * width;
            float z1 = EMITTER_Z + (float) Math.sin(first) * width;
            float x2 = EMITTER_X + (float) Math.cos(second) * width;
            float z2 = EMITTER_Z + (float) Math.sin(second) * width;
            // Each end vertex intersects the horizontal workbench even while the nozzle is tilted.
            float endY1 = EMITTER_Y - length - (x1 - EMITTER_X) * slope;
            float endY2 = EMITTER_Y - length - (x2 - EMITTER_X) * slope;
            vertices.addVertex(matrix, x1, EMITTER_Y, z1).setColor(BEAM_COLOR);
            vertices.addVertex(matrix, x2, EMITTER_Y, z2).setColor(BEAM_COLOR);
            vertices.addVertex(matrix, x2, endY2, z2).setColor(BEAM_COLOR);
            vertices.addVertex(matrix, x1, endY1, z1).setColor(BEAM_COLOR);
        }
    }
}
