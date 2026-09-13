package com.fish_dan_.data_energistics.client.render.overlay;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.ae2.patternprovider.adaptive.AdaptivePatternProviderLogic;
import com.fish_dan_.data_energistics.api.registry.adaptive.AdaptiveProviderConnectorMode;
import com.fish_dan_.data_energistics.client.render.overlay.connector.ConnectorLinkGeometry;
import com.fish_dan_.data_energistics.item.connector.DataDistributionConnectorItem;
import com.fish_dan_.data_energistics.item.connector.DataDistributionConnectorItemData;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;

import java.util.OptionalDouble;

/** Renders client-synchronized bindings while their connector is held in either hand. */
@EventBusSubscriber(modid = Data_Energistics.MODID, value = Dist.CLIENT)
public final class DataDistributionConnectorLinkRenderer {

    private static final Color INPUT_CURRENT = new Color(0.2F, 0.85F, 1.0F, 1.0F);
    private static final Color INPUT_OTHER = new Color(0.08F, 0.38F, 0.65F, 0.75F);
    private static final Color OUTPUT_CURRENT = new Color(0.85F, 0.35F, 1.0F, 1.0F);
    private static final Color OUTPUT_OTHER = new Color(0.42F, 0.16F, 0.62F, 0.75F);
    private static final Color MISSING = new Color(1.0F, 0.2F, 0.2F, 0.85F);
    private static final Color UNLOADED = new Color(0.6F, 0.6F, 0.6F, 0.7F);
    private static final RenderType LINK_LINES = RenderType.create(
            "data_energistics_connector_links", DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.LINES,
            1536, false, false, RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_LINES_SHADER)
                    .setLineState(new RenderStateShard.LineStateShard(OptionalDouble.of(2.0D)))
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setOutputState(RenderStateShard.MAIN_TARGET)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                    .createCompositeState(false));

    private DataDistributionConnectorLinkRenderer() {}

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.player == null) {
            return;
        }
        ItemStack stack = DataDistributionConnectorItem.isConnectorStack(minecraft.player.getMainHandItem()) ? minecraft.player.getMainHandItem() : minecraft.player.getOffhandItem();
        if (!DataDistributionConnectorItem.isConnectorStack(stack)) {
            return;
        }
        DataDistributionConnectorItemData data = DataDistributionConnectorItem.readData(stack);
        if (!data.hasSelection() || !data.isAdaptiveProvider() || !level.dimension().location().toString().equals(data.providerDimensionId())) {
            return;
        }

        BlockPos provider = data.getProviderPos();
        AdaptivePatternProviderLogic logic = DataDistributionConnectorItem.resolveProviderLogic(level, data);
        Vec3 source = data.providerSide() < 0 ? new Vec3(0.5D, 0.5D, 0.5D) : ConnectorLinkGeometry.face(BlockPos.ZERO, Direction.from3DDataValue(data.providerSide())).center();
        Vec3 camera = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        var buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(LINK_LINES);
        pose.pushPose();
        try {
            pose.translate(provider.getX() - camera.x, provider.getY() - camera.y, provider.getZ() - camera.z);
            boolean inputMode = logic == null || logic.connectorMode() == AdaptiveProviderConnectorMode.INPUT;
            Color current = inputMode ? INPUT_CURRENT : OUTPUT_CURRENT;
            Color other = inputMode ? INPUT_OTHER : OUTPUT_OTHER;
            Color sourceColor = logic != null ? current : level.isLoaded(provider) ? MISSING : UNLOADED;
            LevelRenderer.renderLineBox(pose, lines, new AABB(source, source).inflate(0.15D),
                    sourceColor.red(), sourceColor.green(), sourceColor.blue(), sourceColor.alpha());
            if (logic == null) {
                return;
            }
            var targets = logic.connectorTargets();
            int selected = targets.isEmpty() ? -1 : Math.floorMod(data.selectedBindingIndex(), targets.size());
            for (int index = 0; index < targets.size(); index++) {
                var target = targets.get(index);
                Color color = !level.isLoaded(target.position()) ? UNLOADED : level.getBlockState(target.position()).isAir() ? MISSING : index == selected ? current : other;
                // Client capabilities may legitimately be absent for server-only inventories. The synchronized
                // binding is authoritative; only loaded world geometry determines a missing marker here.
                var face = ConnectorLinkGeometry.face(target.position().subtract(provider), target.side());
                line(pose, lines, source, face.approach(), color);
                line(pose, lines, face.approach(), face.center(), color);
                for (int corner = 0; corner < face.corners().size(); corner++) {
                    line(pose, lines, face.corners().get(corner), face.corners().get((corner + 1) % 4), color);
                }
                if (index == selected) {
                    line(pose, lines, face.corners().get(0), face.corners().get(2), color);
                    line(pose, lines, face.corners().get(1), face.corners().get(3), color);
                }
            }
        } finally {
            buffers.endBatch(LINK_LINES);
            pose.popPose();
        }
    }

    private static void line(PoseStack pose, VertexConsumer vertices, Vec3 from, Vec3 to, Color color) {
        Vec3 direction = to.subtract(from);
        if (direction.lengthSqr() < 1.0E-10D) {
            return;
        }
        Vec3 normal = direction.normalize();
        var transform = pose.last();
        vertices.addVertex(transform.pose(), (float) from.x, (float) from.y, (float) from.z)
                .setColor(color.red(), color.green(), color.blue(), color.alpha())
                .setNormal(transform, (float) normal.x, (float) normal.y, (float) normal.z);
        vertices.addVertex(transform.pose(), (float) to.x, (float) to.y, (float) to.z)
                .setColor(color.red(), color.green(), color.blue(), color.alpha())
                .setNormal(transform, (float) normal.x, (float) normal.y, (float) normal.z);
    }

    private record Color(float red, float green, float blue, float alpha) {}
}
