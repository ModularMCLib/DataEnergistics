package com.fish_dan_.data_energistics.client.render.item.lightsaber;

import com.fish_dan_.data_energistics.common.data.DEDataComponents;
import com.fish_dan_.data_energistics.common.data.DEItems;
import com.fish_dan_.data_energistics.common.entity.projectile.OrbitingLightSaberEntity;

import appeng.api.ids.AEComponents;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

/** Draws one synchronized orbit center as a deterministic ring of ordinary saber item models. */
public final class OrbitingLightSaberRenderer extends EntityRenderer<OrbitingLightSaberEntity> {

    private final ItemRenderer itemRenderer;
    private final ItemStack sword = DEItems.DATA_LIGHT_SABER.toStack();

    public OrbitingLightSaberRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.itemRenderer = context.getItemRenderer();
        this.sword.set(AEComponents.STORED_ENERGY, 1.0D);
    }

    @Override
    public void render(OrbitingLightSaberEntity entity, float yaw, float partialTick, PoseStack poses,
                       MultiBufferSource buffers, int light) {
        int count = entity.getSwordCount();
        this.sword.remove(DEDataComponents.LIGHT_SABER_COLOR.get());
        for (DyeColor color : DyeColor.values()) {
            if (color.getTextureDiffuseColor() == entity.getBladeColor()) {
                this.sword.set(DEDataComponents.LIGHT_SABER_COLOR.get(), color.getName());
                break;
            }
        }
        for (int i = 0; i < count; i++) {
            float angle = entity.getOrbitAngle(partialTick) + i * 360.0F / count;
            poses.pushPose();
            poses.mulPose(Axis.YP.rotationDegrees(angle));
            poses.translate(2.2, 1, 0);
            poses.mulPose(Axis.YP.rotationDegrees(90));
            poses.scale(0.8F, 0.8F, 0.8F);
            this.itemRenderer.renderStatic(this.sword, ItemDisplayContext.NONE, LightTexture.FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY, poses, buffers, entity.level(), entity.getId() + i);
            poses.popPose();
        }
        super.render(entity, yaw, partialTick, poses, buffers, light);
    }

    @Override
    public boolean shouldRender(OrbitingLightSaberEntity entity, Frustum frustum, double x, double y, double z) {
        return entity.shouldRender(x, y, z) && frustum.isVisible(entity.getBoundingBox().inflate(3));
    }

    @Override
    public ResourceLocation getTextureLocation(OrbitingLightSaberEntity entity) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
