package com.fish_dan_.data_energistics.client.render.overlay;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.ae2.patternprovider.adaptive.AdaptivePatternProviderLogic;
import com.fish_dan_.data_energistics.item.connector.DataDistributionConnectorItem;
import com.fish_dan_.data_energistics.item.connector.DataDistributionConnectorItemData;

import appeng.api.AECapabilities;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
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
            BlockPos offset = pos.subtract(provider);
            double[] endpoint = faceCenter(offset, target.side());
            double x = endpoint[0], y = endpoint[1], z = endpoint[2];
            double minX = Math.min(.5, x) - .025, maxX = Math.max(.5, x) + .025;
            double minY = Math.min(.5, y) - .025, maxY = Math.max(.5, y) + .025;
            double minZ = Math.min(.5, z) - .025, maxZ = Math.max(.5, z) + .025;
            boolean active = index == selected;
            LevelRenderer.renderLineBox(pose, consumer, new AABB(minX, minY, minZ, maxX, maxY, maxZ), active ? 1f : .3f, active ? .8f : .6f, .2f, .5f);
            boolean loaded = minecraft.level.isLoaded(pos);
            boolean valid = loaded && hasTargetCapability(minecraft.level, pos, target.side());
            float red = valid ? (active ? 1f : .3f) : 1f;
            float green = valid ? (active ? .8f : .6f) : .15f;
            float blue = valid ? .2f : .15f;
            AABB face = faceBox(offset, target.side());
            LevelRenderer.renderLineBox(pose, consumer, face, red, green, blue, .7f);
            index++;
        }
        minecraft.renderBuffers().bufferSource().endBatch(RenderType.lines());
        pose.popPose();
    }

    private static AABB faceBox(BlockPos offset, Direction side) {
        double x = offset.getX(), y = offset.getY(), z = offset.getZ();
        double e = .015;
        return switch (side) {
            case DOWN -> new AABB(x + .2, y - e, z + .2, x + .8, y + e, z + .8);
            case UP -> new AABB(x + .2, y + 1 - e, z + .2, x + .8, y + 1 + e, z + .8);
            case NORTH -> new AABB(x + .2, y + .2, z - e, x + .8, y + .8, z + e);
            case SOUTH -> new AABB(x + .2, y + .2, z + 1 - e, x + .8, y + .8, z + 1 + e);
            case WEST -> new AABB(x - e, y + .2, z + .2, x + e, y + .8, z + .8);
            case EAST -> new AABB(x + 1 - e, y + .2, z + .2, x + 1 + e, y + .8, z + .8);
        };
    }

    private static double[] faceCenter(BlockPos offset, Direction side) {
        double x = offset.getX() + .5, y = offset.getY() + .5, z = offset.getZ() + .5;
        return switch (side) {
            case DOWN -> new double[]{x, offset.getY(), z};
            case UP -> new double[]{x, offset.getY() + 1, z};
            case NORTH -> new double[]{x, y, offset.getZ()};
            case SOUTH -> new double[]{x, y, offset.getZ() + 1};
            case WEST -> new double[]{offset.getX(), y, z};
            case EAST -> new double[]{offset.getX() + 1, y, z};
        };
    }

    private static boolean hasTargetCapability(ClientLevel level, BlockPos position,
                                               Direction side) {
        var state = level.getBlockState(position);
        var entity = level.getBlockEntity(position);
        return level.getCapability(Capabilities.ItemHandler.BLOCK,
                position, state, entity, side) != null
                || level.getCapability(Capabilities.FluidHandler.BLOCK,
                position, state, entity, side) != null
                || level.getCapability(AECapabilities.GENERIC_INTERNAL_INV,
                position, state, entity, side) != null;
    }
}
