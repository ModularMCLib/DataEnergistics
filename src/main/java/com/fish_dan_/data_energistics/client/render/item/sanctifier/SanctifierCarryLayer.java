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
        SanctifierCarryState state = player == Minecraft.getInstance().player
                ? SanctifierCarryState.fromHotbar(player) : SanctifierCarryState.tracked(player);
        boolean held = player.getMainHandItem().is(DEItems.DATA_SANCTIFIER.get());
        if (!state.carried() && !held) return;
        SanctifierBakedModel model = SanctifierBakedModel.current();
        if (model == null) return;
        ItemStack stack = held ? player.getMainHandItem() : DEItems.DATA_SANCTIFIER.toStack();
        float draw = held ? SanctifierAnimationStates.draw(player, partialTick) : 0;
        SanctifierRig.Frame frame = SanctifierRig.thirdPerson(getParentModel(), player, draw);
        boolean powered = held ? SanctifierAnimationStates.isPowered(stack) : state.powered();
        model.renderAssembly(stack, poses, buffers, light, frame, draw, powered, true);
    }
}
