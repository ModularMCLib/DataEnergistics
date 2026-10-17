package com.fish_dan_.data_energistics.client.render.trident;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import com.mojang.blaze3d.vertex.PoseStack;

public final class DataTridentItemRenderer extends BlockEntityWithoutLevelRenderer {

    public DataTridentItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext displayContext, PoseStack poseStack,
                             MultiBufferSource buffer, int packedLight, int packedOverlay) {
        poseStack.pushPose();
        poseStack.scale(1.0F, -1.0F, -1.0F);
        if (displayContext.firstPerson() || displayContext == ItemDisplayContext.THIRD_PERSON_LEFT_HAND || displayContext == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND) {
            // Slide the grip four model units toward the tail without shifting the projectile model.
            poseStack.translate(0.0F, -4.0F / 16.0F, 0.0F);
        }
        DataTridentModel.render(poseStack, buffer, packedLight, packedOverlay, stack.hasFoil());
        poseStack.popPose();
    }
}
