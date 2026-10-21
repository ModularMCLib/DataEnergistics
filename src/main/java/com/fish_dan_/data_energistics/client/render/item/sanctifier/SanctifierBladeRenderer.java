package com.fish_dan_.data_energistics.client.render.item.sanctifier;

import com.fish_dan_.data_energistics.client.render.item.sanctifier.model.SanctifierBakedModel;
import com.fish_dan_.data_energistics.common.data.DEItems;
import com.fish_dan_.data_energistics.common.entity.projectile.SanctifierBladeEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Matrix4f;

/** The server's execution entity owns timing; this renderer only samples the two visible blades. */
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
        Entity caster = entity.level().getEntity(entity.getCasterId());
        Entity target = entity.level().getEntity(entity.getTargetId());
        if (caster == null || target == null) return;
        float tick = entity.getPhaseTicks() + partialTick;
        Vec3 origin = entity.getPosition(partialTick);
        Vec3 casterPosition = caster.getPosition(partialTick);
        Vec3 targetPosition = target.getPosition(partialTick).add(0, target.getBbHeight() * 0.58, 0);
        Vec3 direction = targetPosition.subtract(casterPosition.add(0, caster.getEyeHeight(), 0)).normalize();
        Matrix4f secondary = new Matrix4f().rotationY((float) Math.atan2(direction.x, direction.z))
                .rotateX((float) (Math.PI / 2 - Math.asin(direction.y))).scale(0.72F);
        model.renderSecondary(this.sword, poses, buffers, light, secondary, entity.getBladeColor(), true);
        Minecraft minecraft = Minecraft.getInstance();
        boolean firstPerson = caster == minecraft.player && minecraft.options.getCameraType().isFirstPerson();
        if (tick >= 18 && tick < 30 && !firstPerson) {
            Vec3 anchor = casterPosition.add(0, caster.getEyeHeight() - 0.3, 0).subtract(origin);
            float stroke = Mth.clamp((tick - 18) / 8, 0, 1);
            float rotation = -125 + 250 * stroke;
            Matrix4f primary = new Matrix4f().translation((float) anchor.x, (float) anchor.y, (float) anchor.z)
                    .rotateY((float) (Math.atan2(direction.x, direction.z) + Math.PI))
                    .rotateZ((float) Math.toRadians(rotation)).rotateX(0.85F).scale(0.72F);
            model.renderPrimary(this.sword, poses, buffers, light, primary);
        }
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
