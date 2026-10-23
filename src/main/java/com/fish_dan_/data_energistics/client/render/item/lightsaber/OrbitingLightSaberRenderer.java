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

/** Draws the synchronized orbit center as one or two opposing rings of ordinary saber item models. */
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
        int rings = entity.getRingCount();
        int swordsPerRing = entity.getSwordCount() / rings;
        float baseAngle = entity.getOrbitAngle(partialTick);
        this.sword.remove(DEDataComponents.LIGHT_SABER_COLOR.get());
        for (DyeColor color : DyeColor.values()) {
            if (color.getTextureDiffuseColor() == entity.getBladeColor()) {
                this.sword.set(DEDataComponents.LIGHT_SABER_COLOR.get(), color.getName());
                break;
            }
        }
        for (int ring = 0; ring < rings; ring++) {
            float ringAngle = ring == 0 ? baseAngle : -baseAngle;
            for (int index = 0; index < swordsPerRing; index++) {
                float angle = ringAngle + index * 360.0F / swordsPerRing;
                poses.pushPose();
                poses.mulPose(OrbitingLightSaberPose.itemTransform(angle, ring));
                this.itemRenderer.renderStatic(this.sword, ItemDisplayContext.NONE, LightTexture.FULL_BRIGHT,
                        OverlayTexture.NO_OVERLAY, poses, buffers, entity.level(),
                        entity.getId() + ring * swordsPerRing + index);
                poses.popPose();
            }
        }
        super.render(entity, yaw, partialTick, poses, buffers, light);
    }

    @Override
    public boolean shouldRender(OrbitingLightSaberEntity entity, Frustum frustum, double x, double y, double z) {
        return entity.shouldRender(x, y, z) && frustum.isVisible(entity.getBoundingBox().inflate(4));
    }

    @Override
    public ResourceLocation getTextureLocation(OrbitingLightSaberEntity entity) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
