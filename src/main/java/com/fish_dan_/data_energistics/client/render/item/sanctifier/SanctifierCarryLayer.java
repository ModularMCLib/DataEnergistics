package com.fish_dan_.data_energistics.client.render.item.sanctifier;

import com.fish_dan_.data_energistics.item.powered.sanctifier.SanctifierCarryState;
import com.fish_dan_.data_energistics.registry.DEItems;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.item.ItemStack;

import com.mojang.blaze3d.vertex.PoseStack;

/** Hotbar weapons hang on the left hip and lift from the support hand's carrying handle. */
public final class SanctifierCarryLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    public SanctifierCarryLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack poses, MultiBufferSource buffers, int light, AbstractClientPlayer player,
                       float limbSwing, float limbAmount, float partialTick, float age, float yaw, float pitch) {
        if (player.isInvisible() || player.isSpectator()) return;
        SanctifierCarryState state = player == Minecraft.getInstance().player ? SanctifierCarryState.fromHotbar(player) : SanctifierCarryState.tracked(player);
        ItemStack stack = SanctifierAnimationStates.renderStack(player);
        boolean animated = !stack.isEmpty();
        if (!state.carried() && !animated) return;
        SanctifierBakedModel model = SanctifierBakedModel.current();
        if (model == null) return;
        if (!animated) stack = DEItems.DATA_SANCTIFIER.toStack();
        float draw = animated ? SanctifierAnimationStates.draw(player, partialTick) : 0;
        SanctifierRig.Frame frame = SanctifierRig.thirdPerson(getParentModel(), player, draw);
        boolean powered = animated ? SanctifierAnimationStates.isPowered(stack) : state.powered();
        model.renderAssembly(stack, poses, buffers, light, frame, draw, powered, true);
    }
}
