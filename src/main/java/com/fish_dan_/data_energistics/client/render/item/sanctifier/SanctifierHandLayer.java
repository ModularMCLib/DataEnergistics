package com.fish_dan_.data_energistics.client.render.item.sanctifier;

import com.fish_dan_.data_energistics.client.render.item.blade.BladeThirdPersonPose;
import com.fish_dan_.data_energistics.client.render.item.blade.animation.BladeAttackPlayback;
import com.fish_dan_.data_energistics.client.render.item.sanctifier.model.SanctifierBakedModel;
import com.fish_dan_.data_energistics.common.data.DEItems;
import com.fish_dan_.data_energistics.common.item.powered.LightSaberColorData;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.PlayerItemInHandLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import org.joml.Matrix4f;

/** Keeps the secondary on the physical left hip until charging lifts it into the left hand. */
public final class SanctifierHandLayer extends PlayerItemInHandLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    private float partialTick;

    public SanctifierHandLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent,
                               ItemInHandRenderer itemRenderer) {
        super(parent, itemRenderer);
    }

    @Override
    protected void renderArmWithItem(LivingEntity entity, ItemStack stack, ItemDisplayContext context,
                                     HumanoidArm arm, PoseStack poses, MultiBufferSource buffers, int light) {
        ItemStack weapon = entity.getMainHandItem();
        SanctifierCarryPlayback carry = entity instanceof Player player ? SanctifierCarryPlayback.find(player) : null;
        var execution = SanctifierHandPresentation.execution(entity);
        if (arm == HumanoidArm.RIGHT && (execution != null || carry != null && carry.state().carried() && carry.transitioning())) return;
        Matrix4f motion = BladeAttackPlayback.motion(entity, partialTick);
        if (!weapon.is(DEItems.DATA_SANCTIFIER.get())) {
            if (weapon.is(DEItems.DATA_LIGHT_SABER.get()) && arm == entity.getMainArm() && motion != null) {
                renderAttack(entity, weapon, arm, poses, buffers, light, motion);
                return;
            }
            super.renderArmWithItem(entity, stack, context, arm, poses, buffers, light);
            return;
        }
        if (arm == HumanoidArm.LEFT) {
            if (!SanctifierSecondaryPose.isCharging(entity)) super.renderArmWithItem(entity, entity.getOffhandItem(), context, arm, poses, buffers, light);
            return;
        }
        if (motion != null) {
            renderAttack(entity, weapon, arm, poses, buffers, light, motion);
            return;
        }
        super.renderArmWithItem(entity, weapon, context, arm, poses, buffers, light);
    }

    private void renderAttack(LivingEntity entity, ItemStack weapon, HumanoidArm arm,
                              PoseStack poses, MultiBufferSource buffers, int light, Matrix4f motion) {
        poses.pushPose();
        boolean left = arm == HumanoidArm.LEFT;
        Matrix4f transform = BladeThirdPersonPose.attack(this.getParentModel(), entity, weapon, arm, motion);
        if (weapon.is(DEItems.DATA_SANCTIFIER.get())) {
            SanctifierBakedModel model = SanctifierBakedModel.current();
            if (model != null) model.renderPrimary(weapon, poses, buffers, light, transform);
        } else {
            var renderer = Minecraft.getInstance().getItemRenderer();
            var model = renderer.getModel(weapon, entity.level(), entity, entity.getId());
            poses.mulPose(transform);
            renderer.render(weapon, ItemDisplayContext.NONE, left, poses, buffers, light, OverlayTexture.NO_OVERLAY, model);
        }
        poses.popPose();
    }

    @Override
    public void render(PoseStack poses, MultiBufferSource buffers, int light, AbstractClientPlayer player,
                       float limbSwing, float limbAmount, float partialTick, float age, float headYaw, float headPitch) {
        this.partialTick = partialTick;
        super.render(poses, buffers, light, player, limbSwing, limbAmount, partialTick, age, headYaw, headPitch);
        if (player.isSpectator() || player.isInvisible()) return;
        SanctifierCarryPlayback carry = SanctifierCarryPlayback.find(player);
        if (carry == null || !carry.state().carried()) return;
        ItemStack weapon = carry.weapon();
        SanctifierBakedModel model = SanctifierBakedModel.current();
        if (model == null) return;
        PoseStack body = new PoseStack();
        this.getParentModel().body.translateAndRotate(body);
        Matrix4f bodyTransform = body.last().pose();
        poses.pushPose();
        if (this.getParentModel().young) {
            poses.translate(0, 0.75, 0);
            poses.scale(0.5F, 0.5F, 0.5F);
        }
        if ((!carry.selected() || carry.transitioning()) && SanctifierHandPresentation.execution(player) == null) {
            Matrix4f back = SanctifierCarryPose.mounted(bodyTransform);
            Matrix4f held = BladeThirdPersonPose.restingHand(this.getParentModel(), player, weapon, HumanoidArm.RIGHT);
            model.renderPrimary(weapon, poses, buffers, light,
                    SanctifierCarryPose.draw(back, held, bodyTransform, carry.progress(partialTick)));
        }
        if (carry.state().deployed()) {
            poses.popPose();
            return;
        }
        Matrix4f mount = SanctifierSecondaryPose.mounted(bodyTransform);
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
        model.renderSecondary(weapon, poses, buffers, light, transform,
                LightSaberColorData.getSanctifierAnimatedColor(player.level().getGameTime()),
                carry.state().powered());
        poses.popPose();
    }
}
