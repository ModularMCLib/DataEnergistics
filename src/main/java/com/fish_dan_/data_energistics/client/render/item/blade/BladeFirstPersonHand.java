package com.fish_dan_.data_energistics.client.render.item.blade;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.neoforged.neoforge.client.ClientHooks;

import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Matrix4f;

/** Renders the player's own fist and sleeve at the weapon grip instead of leaving the primary floating onscreen. */
public final class BladeFirstPersonHand {

    private BladeFirstPersonHand() {}

    public static void render(AbstractClientPlayer player, PoseStack poses, MultiBufferSource buffers, int light,
                              Matrix4f grip, HumanoidArm side) {
        if (player.isInvisible()) return;
        if (!(Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(player) instanceof PlayerRenderer renderer)) return;
        boolean left = side == HumanoidArm.LEFT;
        var model = renderer.getModel();
        var arm = left ? model.leftArm : model.rightArm;
        var sleeve = left ? model.leftSleeve : model.rightSleeve;
        PartPose armPose = arm.storePose();
        PartPose sleevePose = sleeve.storePose();
        poses.pushPose();
        poses.mulPose(BladeGripPose.firstPersonArm(grip, left, player.getSkin().model() == PlayerSkin.Model.SLIM));
        try {
            if (ClientHooks.renderSpecificFirstPersonArm(poses, buffers, light, player, side)) return;
            arm.loadPose(PartPose.ZERO);
            sleeve.loadPose(PartPose.ZERO);
            var texture = player.getSkin().texture();
            arm.render(poses, buffers.getBuffer(RenderType.entitySolid(texture)), light, OverlayTexture.NO_OVERLAY);
            if (player.isModelPartShown(left ? PlayerModelPart.LEFT_SLEEVE : PlayerModelPart.RIGHT_SLEEVE)) {
                sleeve.render(poses, buffers.getBuffer(RenderType.entityTranslucent(texture)), light, OverlayTexture.NO_OVERLAY);
            }
        } finally {
            arm.loadPose(armPose);
            sleeve.loadPose(sleevePose);
            poses.popPose();
        }
    }
}
