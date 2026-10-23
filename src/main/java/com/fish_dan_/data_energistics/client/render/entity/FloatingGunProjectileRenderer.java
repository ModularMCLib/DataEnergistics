package com.fish_dan_.data_energistics.client.render.entity;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.weapon.platform.model.GunForm;
import com.fish_dan_.data_energistics.common.weapon.platform.projectile.FloatingGunProjectile;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

import com.mojang.blaze3d.vertex.PoseStack;

/** Small luminous cores; server particles carry their flight and electric connections. */
public final class FloatingGunProjectileRenderer extends EntityRenderer<FloatingGunProjectile> {

    private static final ResourceLocation TEXTURE = Data_Energistics.id("textures/entity/floating_gun_projectile.png");

    public FloatingGunProjectileRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(FloatingGunProjectile entity, float yaw, float partialTick, PoseStack poses, MultiBufferSource buffers, int light) {
        poses.pushPose();
        poses.mulPose(entityRenderDispatcher.cameraOrientation());
        float size = entity.form() == GunForm.PLASMA ? 0.16f : 0.08f;
        int color = entity.form() == GunForm.MEDICAL ? 0x75ffac : entity.ammunition().color;
        var vertex = buffers.getBuffer(RenderType.entityTranslucentEmissive(TEXTURE));
        var pose = poses.last();
        vertex.addVertex(pose, -size, -size, 0).setColor(0xff000000 | color).setUv(0, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xf000f0).setNormal(pose, 0, 0, 1);
        vertex.addVertex(pose, size, -size, 0).setColor(0xff000000 | color).setUv(1, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xf000f0).setNormal(pose, 0, 0, 1);
        vertex.addVertex(pose, size, size, 0).setColor(0xff000000 | color).setUv(1, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xf000f0).setNormal(pose, 0, 0, 1);
        vertex.addVertex(pose, -size, size, 0).setColor(0xff000000 | color).setUv(0, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xf000f0).setNormal(pose, 0, 0, 1);
        poses.popPose();
        super.render(entity, yaw, partialTick, poses, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(FloatingGunProjectile entity) {
        return TEXTURE;
    }
}
