package com.fish_dan_.data_energistics.client.render.item.sanctifier;

import com.fish_dan_.data_energistics.client.render.item.sanctifier.model.SanctifierBakedModel;
import com.fish_dan_.data_energistics.common.data.DEDataComponents;
import com.fish_dan_.data_energistics.common.data.DEItems;
import com.fish_dan_.data_energistics.common.item.powered.LightSaberColorData;
import com.fish_dan_.data_energistics.common.item.powered.PoweredSwordItem;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.PlayerItemInHandLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import org.joml.Matrix4f;

/** Keeps the secondary on the physical left hip until charging lifts it into the left hand. */
public final class SanctifierHandLayer extends PlayerItemInHandLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    public SanctifierHandLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent,
                               ItemInHandRenderer itemRenderer) {
        super(parent, itemRenderer);
    }

    @Override
    protected void renderArmWithItem(LivingEntity entity, ItemStack stack, ItemDisplayContext context,
                                     HumanoidArm arm, PoseStack poses, MultiBufferSource buffers, int light) {
        ItemStack weapon = entity.getMainHandItem();
        if (!weapon.is(DEItems.DATA_SANCTIFIER.get())) {
            super.renderArmWithItem(entity, stack, context, arm, poses, buffers, light);
            return;
        }
        if (arm == HumanoidArm.LEFT) {
            if (!SanctifierSecondaryPose.isCharging(entity)) super.renderArmWithItem(entity, entity.getOffhandItem(), context, arm, poses, buffers, light);
            return;
        }
        var execution = SanctifierHandPresentation.execution(entity);
        if (execution != null) return;
        super.renderArmWithItem(entity, weapon, context, arm, poses, buffers, light);
    }

    @Override
    public void render(PoseStack poses, MultiBufferSource buffers, int light, AbstractClientPlayer player,
                       float limbSwing, float limbAmount, float partialTick, float age, float headYaw, float headPitch) {
        super.render(poses, buffers, light, player, limbSwing, limbAmount, partialTick, age, headYaw, headPitch);
        ItemStack weapon = player.getMainHandItem();
        if (!weapon.is(DEItems.DATA_SANCTIFIER.get()) || weapon.has(DEDataComponents.SANCTIFIER_BLADE.get())) return;
        SanctifierBakedModel model = SanctifierBakedModel.current();
        if (model == null) return;
        PoseStack body = new PoseStack();
        this.getParentModel().body.translateAndRotate(body);
        Matrix4f mount = SanctifierSecondaryPose.mounted(body.last().pose());
        Matrix4f transform = mount;
        if (SanctifierSecondaryPose.isCharging(player)) {
            PoseStack hand = new PoseStack();
            this.getParentModel().translateToHand(HumanoidArm.LEFT, hand);
            hand.mulPose(Axis.XP.rotationDegrees(-90));
            hand.mulPose(Axis.YP.rotationDegrees(180));
            hand.translate(-1 / 16.0, 0.125, -0.625);
            Matrix4f held = new Matrix4f(hand.last().pose()).mul(model.secondaryHandTransform(ItemDisplayContext.THIRD_PERSON_LEFT_HAND, true));
            transform = SanctifierSecondaryPose.pickup(mount, held, SanctifierSecondaryPose.pickupProgress(player, partialTick));
        }
        poses.pushPose();
        if (this.getParentModel().young) {
            poses.translate(0, 0.75, 0);
            poses.scale(0.5F, 0.5F, 0.5F);
        }
        model.renderSecondary(weapon, poses, buffers, light, transform,
                LightSaberColorData.getSanctifierAnimatedColor(player.level().getGameTime()),
                ((PoweredSwordItem) weapon.getItem()).getAECurrentPower(weapon) > 0);
        poses.popPose();
    }
}
