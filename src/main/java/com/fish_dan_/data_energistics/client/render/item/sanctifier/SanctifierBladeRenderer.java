package com.fish_dan_.data_energistics.client.render.item.sanctifier;

import com.fish_dan_.data_energistics.client.render.item.sanctifier.model.SanctifierBakedModel;
import com.fish_dan_.data_energistics.common.data.DEItems;
import com.fish_dan_.data_energistics.common.entity.projectile.SanctifierBladeEntity;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;

import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Matrix4f;

/** Renders the thrown secondary; the player's hand layer samples the same entity clock for the primary. */
public final class SanctifierBladeRenderer extends EntityRenderer<SanctifierBladeEntity> {

    private final ItemStack sword = DEItems.DATA_SANCTIFIER.toStack();

    public SanctifierBladeRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(SanctifierBladeEntity entity, float yaw, float partialTick, PoseStack poses,
                       MultiBufferSource buffers, int light) {
        SanctifierBakedModel model = SanctifierBakedModel.current();
        if (model == null) return;
        float tick = entity.getPhaseTicks() + partialTick;
        float bladeYaw = Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot());
        float bladePitch = Mth.lerp(partialTick, entity.xRotO, entity.getXRot());
        // Blend the grip anchor into the impact anchor so the throw starts at the left hand and ends tip-first.
        float tipOffset = 2.12F * Mth.clamp(tick / 8, 0, 1);
        Matrix4f secondary = new Matrix4f().rotationY((float) Math.toRadians(bladeYaw))
                .rotateX((float) Math.toRadians(90 + bladePitch)).scale(0.72F).translate(0, -tipOffset, 0);
        model.renderSecondary(this.sword, poses, buffers, light, secondary, entity.getBladeColor(), true);
        super.render(entity, yaw, partialTick, poses, buffers, light);
    }

    @Override
    public boolean shouldRender(SanctifierBladeEntity entity, Frustum frustum, double x, double y, double z) {
        return entity.shouldRender(x, y, z) && frustum.isVisible(entity.getBoundingBox().inflate(16));
    }

    @Override
    public ResourceLocation getTextureLocation(SanctifierBladeEntity entity) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
