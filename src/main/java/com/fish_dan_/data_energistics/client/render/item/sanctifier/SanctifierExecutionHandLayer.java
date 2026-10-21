package com.fish_dan_.data_energistics.client.render.item.sanctifier;

import com.fish_dan_.data_energistics.common.data.DEItems;

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

/** Prevents the ordinary held sword duplicating the entity renderer's execution stroke. */
public final class SanctifierExecutionHandLayer extends PlayerItemInHandLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    public SanctifierExecutionHandLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent,
                                        ItemInHandRenderer itemRenderer) {
        super(parent, itemRenderer);
    }

    @Override
    protected void renderArmWithItem(LivingEntity entity, ItemStack stack, ItemDisplayContext context,
                                     HumanoidArm arm, PoseStack poses, MultiBufferSource buffers, int light) {
        var execution = SanctifierExecutionPresentation.execution(entity);
        if (arm == entity.getMainArm() && stack.is(DEItems.DATA_SANCTIFIER.get()) && execution != null && execution.getPhaseTicks() >= 18) return;
        super.renderArmWithItem(entity, stack, context, arm, poses, buffers, light);
    }
}
