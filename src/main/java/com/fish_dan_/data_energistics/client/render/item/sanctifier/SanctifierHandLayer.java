package com.fish_dan_.data_energistics.client.render.item.sanctifier;

import com.fish_dan_.data_energistics.client.render.item.sanctifier.model.SanctifierBakedModel;
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

/** Holds the primary and secondary blades in opposite hands and releases the secondary during an execution. */
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
        var execution = SanctifierHandPresentation.execution(entity);
        if (arm != entity.getMainArm()) {
            if (execution != null) return;
            SanctifierBakedModel model = SanctifierBakedModel.current();
            if (model == null) return;
            poses.pushPose();
            this.getParentModel().translateToHand(arm, poses);
            poses.mulPose(Axis.XP.rotationDegrees(-90));
            poses.mulPose(Axis.YP.rotationDegrees(180));
            boolean leftHand = arm == HumanoidArm.LEFT;
            poses.translate((leftHand ? -1 : 1) / 16.0, 0.125, -0.625);
            model.renderHeldSecondary(weapon, context, leftHand, poses, buffers, light,
                    LightSaberColorData.getSanctifierAnimatedColor(entity.level().getGameTime()),
                    ((PoweredSwordItem) weapon.getItem()).getAECurrentPower(weapon) > 0);
            poses.popPose();
            return;
        }
        if (arm == entity.getMainArm() && stack.is(DEItems.DATA_SANCTIFIER.get()) && execution != null && execution.getPhaseTicks() >= 18) return;
        super.renderArmWithItem(entity, stack, context, arm, poses, buffers, light);
    }
}
