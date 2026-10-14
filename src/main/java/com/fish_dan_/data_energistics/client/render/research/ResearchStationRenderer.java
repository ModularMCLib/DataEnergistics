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
        float phase = (level.getGameTime() + partialTick) * 0.09f;
        float offset = working ? (float) Math.sin(phase) * 0.08f : 0;
        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        float angle = switch (host.getBlockState().getValue(ResearchStationBlock.FACING)) {
            case SOUTH -> 0;
            case WEST -> -90;
            case NORTH -> 180;
            case EAST -> 90;
            default -> throw new IllegalStateException("Research facing must be horizontal");
        };
        pose.mulPose(Axis.YP.rotationDegrees(angle));
        pose.translate(-0.5, 0, -0.5);
        pose.pushPose();
        pose.translate(-15.0 / 16 + offset, 22.0 / 16, 0);
        var model = Minecraft.getInstance().getModelManager().getModel(working ? HEAD_ON : HEAD_OFF);
        var state = host.getBlockState();
        for (RenderType layer : model.getRenderTypes(state, RandomSource.create(42), ModelData.EMPTY)) {
            renderer.getModelRenderer().renderModel(pose.last(), buffers.getBuffer(RenderTypeHelper.getEntityRenderType(layer, false)),
                    state, model, 1, 1, 1, light, overlay, ModelData.EMPTY, layer);
        }
        pose.popPose();
        if (working) beam(pose, buffers.getBuffer(RenderType.lightning()), -7.0f / 16 + offset);
        pose.popPose();
    }

    private static void beam(PoseStack pose, VertexConsumer vertices, float x) {
        var matrix = pose.last().pose();
        float width = 0.014f;
        for (int side = 0; side < 4; side++) {
            double first = side * Math.PI / 2;
            double second = (side + 1) * Math.PI / 2;
            float x1 = x + (float) Math.cos(first) * width;
            float z1 = 0.5f + (float) Math.sin(first) * width;
            float x2 = x + (float) Math.cos(second) * width;
            float z2 = 0.5f + (float) Math.sin(second) * width;
            vertices.addVertex(matrix, x1, 1.75f, z1).setColor(110, 220, 255, 180);
            vertices.addVertex(matrix, x1, 0.55f, z1).setColor(110, 220, 255, 180);
            vertices.addVertex(matrix, x2, 0.55f, z2).setColor(110, 220, 255, 180);
            vertices.addVertex(matrix, x2, 1.75f, z2).setColor(110, 220, 255, 180);
        }
    }
}
