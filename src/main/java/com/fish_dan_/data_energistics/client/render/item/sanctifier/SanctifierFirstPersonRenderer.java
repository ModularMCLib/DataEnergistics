package com.fish_dan_.data_energistics.client.render.item.sanctifier;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.RenderHandEvent;

import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Matrix4f;

/** Renders player skin hands at the blade grip and the scabbard carrying handle. */
public final class SanctifierFirstPersonRenderer {

    private SanctifierFirstPersonRenderer() {}

    public static void renderHand(RenderHandEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        var player = minecraft.player;
        if (player == null || player.isSpectator()) return;
        ItemStack stack = SanctifierAnimationStates.renderStack(player);
        if (stack.isEmpty()) return;
        boolean supportFree = player.getOffhandItem().isEmpty();
        float partial = event.getPartialTick();
        boolean support = SanctifierAnimationStates.usesSupportHand(player, partial);
        if (event.getHand() == InteractionHand.OFF_HAND) {
            if (support) event.setCanceled(true);
            return;
        }
        SanctifierBakedModel model = SanctifierBakedModel.current();
        if (model == null) return;
        event.setCanceled(true);
        var input = SanctifierAnimationStates.input(player, InteractionHand.MAIN_HAND, partial);
        float draw = input.draw();
        boolean powered = SanctifierAnimationStates.isPowered(stack);
        var frame = SanctifierRig.firstPerson(player.getMainArm(), input);
        model.renderAssembly(stack, event.getPoseStack(), event.getMultiBufferSource(),
                event.getPackedLight(), frame.assembly(), draw, powered, supportFree && draw < 1);
        if (!player.isInvisible()) {
            arm(player, player.getMainArm(), frame.mainHand(),
                    event.getPoseStack(), event.getMultiBufferSource(), event.getPackedLight());
            if (support) {
                arm(player, player.getMainArm().getOpposite(), frame.supportHand(),
                        event.getPoseStack(), event.getMultiBufferSource(), event.getPackedLight());
            }
        }
    }

    private static void arm(AbstractClientPlayer player, HumanoidArm arm, Matrix4f hand,
                            PoseStack poses, MultiBufferSource buffers, int light) {
        if (!(Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(player) instanceof PlayerRenderer renderer)) return;
        float side = arm == HumanoidArm.RIGHT ? 1 : -1;
        boolean right = arm == HumanoidArm.RIGHT;
        ModelPart skin = right ? renderer.getModel().rightArm : renderer.getModel().leftArm;
        ModelPart sleeve = right ? renderer.getModel().rightSleeve : renderer.getModel().leftSleeve;
        PartPose skinPose = skin.storePose();
        PartPose sleevePose = sleeve.storePose();
        float palmX = player.getSkin().model() == PlayerSkin.Model.SLIM ? 0.5F : 1;
        poses.pushPose();
        poses.mulPose(hand);
        // The blade passes through the fist center, rather than touching the knuckle surface.
        poses.translate(side * palmX / 16, -8.0F / 16, 0);
        // Neutral local arms put the palm inside the grip, independent of third-person arm poses.
        skin.loadPose(PartPose.ZERO);
        sleeve.loadPose(PartPose.ZERO);
        try {
            skin.render(poses, buffers.getBuffer(RenderType.entitySolid(player.getSkin().texture())),
                    light, OverlayTexture.NO_OVERLAY);
            if (player.isModelPartShown(right ? PlayerModelPart.RIGHT_SLEEVE : PlayerModelPart.LEFT_SLEEVE)) {
                sleeve.render(poses, buffers.getBuffer(RenderType.entityTranslucent(player.getSkin().texture())),
                        light, OverlayTexture.NO_OVERLAY);
            }
        } finally {
            skin.loadPose(skinPose);
            sleeve.loadPose(sleevePose);
            poses.popPose();
        }
    }
}
