package com.fish_dan_.data_energistics.client.render.item.blade;

import com.fish_dan_.data_energistics.client.render.item.blade.animation.BladeAttackPlayback;
import com.fish_dan_.data_energistics.common.data.DEItems;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.neoforged.neoforge.client.ClientHooks;
import net.neoforged.neoforge.client.event.RenderHandEvent;

import org.joml.Matrix4f;

/** Applies native attack motion after the resource's hand transform, preserving model pivots and blade colors. */
public final class BladeAttackPresentation {

    private BladeAttackPresentation() {}

    public static void renderHand(RenderHandEvent event) {
        var player = Minecraft.getInstance().player;
        if (player == null || event.getHand() != InteractionHand.MAIN_HAND || !player.getMainHandItem().is(DEItems.DATA_LIGHT_SABER.get()) || player.isScoping()) return;
        Matrix4f motion = BladeAttackPlayback.motion(player, event.getPartialTick());
        event.setCanceled(true);
        boolean left = player.getMainArm() == HumanoidArm.LEFT;
        var poses = event.getPoseStack();
        var renderer = Minecraft.getInstance().getItemRenderer();
        var stack = player.getMainHandItem();
        BakedModel model = renderer.getModel(stack, player.level(), player, player.getId());
        poses.pushPose();
        poses.translate((left ? -1 : 1) * 0.56F, -0.52F - event.getEquipProgress() * 0.6F, -0.72F);
        model = ClientHooks.handleCameraTransforms(poses, model, left ? ItemDisplayContext.FIRST_PERSON_LEFT_HAND : ItemDisplayContext.FIRST_PERSON_RIGHT_HAND, left);
        // Conjugation mirrors the motion, not the geometry: the resulting matrix remains a proper rigid rotation.
        if (motion != null) {
            if (left) poses.mulPose(new Matrix4f().scaling(-1, 1, 1).mul(motion).scale(-1, 1, 1));
            else poses.mulPose(motion);
        }
        renderer.render(stack, ItemDisplayContext.NONE, left, poses, event.getMultiBufferSource(), event.getPackedLight(), OverlayTexture.NO_OVERLAY, model);
        poses.popPose();
    }
}
