package com.fish_dan_.data_energistics.client.render.overlay;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.ae2.patternprovider.adaptive.AdaptivePatternProviderLogic;
import com.fish_dan_.data_energistics.item.connector.DataDistributionConnectorItem;
import com.fish_dan_.data_energistics.item.connector.DataDistributionConnectorItemData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.phys.AABB;

@EventBusSubscriber(modid = Data_Energistics.MODID, value = Dist.CLIENT)
public final class DataDistributionConnectorLinkRenderer {
    private DataDistributionConnectorLinkRenderer() {}

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) return;
        ItemStack stack = DataDistributionConnectorItem.isConnectorStack(minecraft.player.getMainHandItem())
                ? minecraft.player.getMainHandItem() : minecraft.player.getOffhandItem();
        if (!DataDistributionConnectorItem.isConnectorStack(stack)) return;
        DataDistributionConnectorItemData data = DataDistributionConnectorItem.readData(stack);
        AdaptivePatternProviderLogic logic = DataDistributionConnectorItem.resolveProviderLogic(minecraft.level, data);
        if (logic == null) return;
        BlockPos provider = logic.hostPosition();
        var camera = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(provider.getX() - camera.x, provider.getY() - camera.y, provider.getZ() - camera.z);
        var consumer = minecraft.renderBuffers().bufferSource().getBuffer(RenderType.lines());
        LevelRenderer.renderLineBox(pose, consumer, new AABB(.35, .35, .35, .65, .65, .65), .2f, .8f, 1f, .45f);
        int selected = data.selectedBindingIndex();
        int index = 0;
        for (AdaptivePatternProviderLogic.ConnectorTarget target : logic.connectorTargets()) {
            BlockPos pos = target.position();
            double x = pos.getX() - provider.getX() + .5;
            double y = pos.getY() - provider.getY() + .5;
            double z = pos.getZ() - provider.getZ() + .5;
            double minX = Math.min(.5, x) - .025, maxX = Math.max(.5, x) + .025;
            double minY = Math.min(.5, y) - .025, maxY = Math.max(.5, y) + .025;
            double minZ = Math.min(.5, z) - .025, maxZ = Math.max(.5, z) + .025;
            boolean active = index == selected;
            LevelRenderer.renderLineBox(pose, consumer, new AABB(minX, minY, minZ, maxX, maxY, maxZ), active ? 1f : .3f, active ? .8f : .6f, .2f, .5f);
            AABB face = faceBox(pos.subtract(provider), target.side());
            LevelRenderer.renderLineBox(pose, consumer, face, active ? 1f : .3f, active ? .8f : .6f, .2f, .55f);
            index++;
        }
        minecraft.renderBuffers().bufferSource().endBatch(RenderType.lines());
        pose.popPose();
    }

    private static AABB faceBox(BlockPos offset, Direction side) {
        double x = offset.getX(), y = offset.getY(), z = offset.getZ();
        return new AABB(x + .2, y + .2, z + .2, x + .8, y + .8, z + .8).inflate(.04);
    }
}
