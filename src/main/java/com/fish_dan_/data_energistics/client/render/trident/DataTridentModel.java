package com.fish_dan_.data_energistics.client.render.trident;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

/** Renders the exported geometry in the same coordinate system as the vanilla trident entity model. */
public final class DataTridentModel {

    public static final ModelResourceLocation GEOMETRY = ModelResourceLocation.standalone(
            Data_Energistics.id("item/trident/geometry"));

    private static final RenderType RENDER_TYPE = RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS);

    private DataTridentModel() {}

    /** Called on the client render thread, after the vanilla item or projectile pose has been applied. */
    public static void render(PoseStack poses, MultiBufferSource buffers, int packedLight, int packedOverlay,
                              boolean foil) {
        Minecraft minecraft = Minecraft.getInstance();
        BakedModel model = minecraft.getModelManager().getModel(GEOMETRY);
        VertexConsumer vertices = ItemRenderer.getFoilBufferDirect(buffers, RENDER_TYPE, false, foil);

        poses.pushPose();
        // Keep the authored size of 0.68 and align the diagonal shaft with vanilla's downward model Y axis.
        // The shaft runs through x + y = 16.75; this pivot places the tip/tail near vanilla's -4/+27 Y units.
        poses.scale(0.68F, -0.68F, -0.68F);
        poses.mulPose(Axis.ZP.rotationDegrees(-45.0F));
        poses.translate(3.625D / 16.0D, -20.375D / 16.0D, -8.0D / 16.0D);
        minecraft.getItemRenderer().renderModelLists(model, ItemStack.EMPTY, packedLight, packedOverlay, poses,
                vertices);
        poses.popPose();
    }
}
