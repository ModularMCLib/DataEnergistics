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
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.RenderTypeHelper;
import net.neoforged.neoforge.client.model.data.ModelData;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import org.joml.Matrix4f;

/** Three instances of one laser head, with server-authoritative animation and isolated poses. */
public final class ResearchStationRenderer implements BlockEntityRenderer<ResearchStationBlockEntity> {

    public static final ModelResourceLocation HEAD_OFF = ModelResourceLocation.standalone(Data_Energistics.id("block/research/laser_head_off"));
    public static final ModelResourceLocation HEAD_ON = ModelResourceLocation.standalone(Data_Energistics.id("block/research/laser_head_on"));
    private static final float EMITTER_X = 8F / 16;
    private static final float EMITTER_Y = 6F / 16;
    private static final float EMITTER_Z = 8F / 16;
    private static final float WORKBENCH_X = -8F / 16;
    private static final float WORKBENCH_Y = 18.01F / 16;
    private static final float WORKBENCH_Z = 8F / 16;
    private static final float ITEM_HEIGHT_OFFSET = 2F / 16;
    private static final float SWING_DEGREES = 10;
    private static final float YAW_DEGREES = 14;
    private static final float PITCH_DEGREES = 4;
    private static final int SWING_PERIOD_TICKS = 80;
    private static final int BEAM_COLOR = 0x80A8FFE0;
    // Side heads exchange the supplied model's axes. Their pivots preserve the original idle geometry.
    private static final HeadPlacement[] HEADS = {
            new HeadPlacement(new Vec3(-13.5, 22.375, 8), new Vec3(7.89403, 7.90433, 8),
                    Axis.ZP, 67.5F, 45, 0, 0),
            new HeadPlacement(new Vec3(-8, 25.25, 14.5), new Vec3(8, 8, 8),
                    Axis.XP, 67.5F, 40, 90, Mth.TWO_PI / 3),
            new HeadPlacement(new Vec3(-7.90433, 30.89403, 8), new Vec3(7.09567, 8.89403, 8),
                    Axis.ZP, 0, 0, 0, Mth.TWO_PI * 2 / 3)
    };
    private final BlockRenderDispatcher renderer;
    private final ItemRenderer itemRenderer;

    public ResearchStationRenderer(BlockEntityRendererProvider.Context context) {
        this.renderer = context.getBlockRenderDispatcher();
        this.itemRenderer = context.getItemRenderer();
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
        var model = Minecraft.getInstance().getModelManager().getModel(working ? HEAD_ON : HEAD_OFF);
        var state = host.getBlockState();
        var layers = model.getRenderTypes(state, RandomSource.create(42), ModelData.EMPTY);
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
            renderTarget(host, pose, buffers, light);
            for (HeadPlacement head : HEADS) {
                Matrix4f placement = head.transform(working, phase);
                pose.pushPose();
                try {
                    pose.mulPose(placement);
                    for (RenderType layer : layers) {
                        renderer.getModelRenderer().renderModel(pose.last(), buffers.getBuffer(RenderTypeHelper.getEntityRenderType(layer, false)),
                                state, model, 1, 1, 1, light, overlay, ModelData.EMPTY, layer);
                    }
                    if (working) beam(pose, buffers.getBuffer(RenderType.lightning()), placement);
                } finally {
                    pose.popPose();
                }
            }
        } finally {
            pose.popPose();
        }
    }

    private void renderTarget(ResearchStationBlockEntity host, PoseStack pose, MultiBufferSource buffers, int light) {
        ItemStack target = host.targetInventory().getStackInSlot(0);
        if (target.isEmpty()) return;
        pose.pushPose();
        try {
            pose.translate(WORKBENCH_X, WORKBENCH_Y + ITEM_HEIGHT_OFFSET, WORKBENCH_Z);
            itemRenderer.renderStatic(target, ItemDisplayContext.GROUND, light, OverlayTexture.NO_OVERLAY,
                    pose, buffers, host.getLevel(), (int) host.getBlockPos().asLong());
        } finally {
            pose.popPose();
        }
    }

    private static void beam(PoseStack pose, VertexConsumer vertices, Matrix4f placement) {
        var matrix = pose.last().pose();
        // Use the station-local transform; the shared pose already includes camera and facing rotations.
        float nozzleY = placement.m01() * EMITTER_X + placement.m11() * EMITTER_Y + placement.m21() * EMITTER_Z + placement.m31();
        float length = (nozzleY - WORKBENCH_Y) / placement.m11();
        float slopeX = placement.m01() / placement.m11();
        float slopeZ = placement.m21() / placement.m11();
        float width = 0.014f;
        for (int side = 0; side < 4; side++) {
            double first = side * Math.PI / 2;
            double second = (side + 1) * Math.PI / 2;
            float x1 = EMITTER_X + (float) Math.cos(first) * width;
            float z1 = EMITTER_Z + (float) Math.sin(first) * width;
            float x2 = EMITTER_X + (float) Math.cos(second) * width;
            float z2 = EMITTER_Z + (float) Math.sin(second) * width;
            // Each end vertex intersects the horizontal workbench even while the nozzle is tilted.
            float endY1 = EMITTER_Y - length - (x1 - EMITTER_X) * slopeX - (z1 - EMITTER_Z) * slopeZ;
            float endY2 = EMITTER_Y - length - (x2 - EMITTER_X) * slopeX - (z2 - EMITTER_Z) * slopeZ;
            vertices.addVertex(matrix, x1, EMITTER_Y, z1).setColor(BEAM_COLOR);
            vertices.addVertex(matrix, x2, EMITTER_Y, z2).setColor(BEAM_COLOR);
            vertices.addVertex(matrix, x2, endY2, z2).setColor(BEAM_COLOR);
            vertices.addVertex(matrix, x1, endY1, z1).setColor(BEAM_COLOR);
        }
    }

    /** Attachment and model pivot use the original model's pixel coordinates. */
    private record HeadPlacement(Vec3 attachment, Vec3 modelPivot, Axis swingAxis,
                                 float idleDegrees, float workingDegrees, float yawDegrees, float phaseOffset) {

        private Matrix4f transform(boolean working, float phase) {
            Matrix4f transform = new Matrix4f()
                    .translation((float) attachment.x / 16, (float) attachment.y / 16, (float) attachment.z / 16);
            if (working) {
                float headPhase = phase + phaseOffset;
                // Closed, offset cycles scan both axes while keeping each beam on the workbench.
                transform.rotateY(Mth.cos(headPhase) * YAW_DEGREES * Mth.DEG_TO_RAD)
                        .rotate(swingAxis.rotationDegrees(workingDegrees + Mth.sin(headPhase) * SWING_DEGREES))
                        .rotateX(Mth.sin(phase * 2 + phaseOffset) * PITCH_DEGREES * Mth.DEG_TO_RAD);
            } else {
                transform.rotate(swingAxis.rotationDegrees(idleDegrees));
            }
            return transform.rotateY(yawDegrees * Mth.DEG_TO_RAD)
                    .translate((float) -modelPivot.x / 16, (float) -modelPivot.y / 16, (float) -modelPivot.z / 16);
        }
    }
}
