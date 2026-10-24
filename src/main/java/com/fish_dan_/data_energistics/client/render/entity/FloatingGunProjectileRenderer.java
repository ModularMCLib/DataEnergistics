package com.fish_dan_.data_energistics.client.render.entity;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.weapon.platform.model.GunForm;
import com.fish_dan_.data_energistics.common.weapon.platform.projectile.FloatingGunProjectile;
import com.fish_dan_.data_energistics.common.weapon.platform.model.GunAmmunition;
import com.fish_dan_.data_energistics.client.key.AeKeyRenderers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ModelEvent;
import com.mojang.math.Axis;
import java.util.Locale;

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
    private static final ModelResourceLocation[][] MODELS = new ModelResourceLocation[2][5];
    static {
        for (int shape = 0; shape < 2; shape++) for (GunAmmunition ammo : GunAmmunition.values()) MODELS[shape][ammo.ordinal()] = ModelResourceLocation.standalone(
                Data_Energistics.id("weapon_platform/ammunition/" + (shape == 0 ? "bolt_" : "orb_") + ammo.name().toLowerCase(Locale.ROOT)));
    }
    public static void registerModels(ModelEvent.RegisterAdditional event) {
        for (var shape : MODELS) for (var model : shape) event.register(model);
    }

    public FloatingGunProjectileRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(FloatingGunProjectile entity, float yaw, float partialTick, PoseStack poses, MultiBufferSource buffers, int light) {
        poses.pushPose();
        Vec3 direction = entity.form() == GunForm.SNIPER ? entity.beam() : entity.getDeltaMovement();
        if (entity.form() == GunForm.SNIPER) {
            int color = entity.ammunition().key == null ? entity.ammunition().color : AeKeyRenderers.ammunitionColor(entity.ammunition().key);
            var line = buffers.getBuffer(RenderType.lines());
            var normal = direction.normalize();
            var pose = poses.last();
            line.addVertex(pose, 0, 0, 0).setColor(0xff000000 | color).setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
            line.addVertex(pose, (float) direction.x, (float) direction.y, (float) direction.z).setColor(0xff000000 | color).setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
        } else {
            poses.mulPose(Axis.YP.rotation((float) Math.atan2(direction.x, direction.z)));
            poses.mulPose(Axis.XP.rotation((float) -Math.atan2(direction.y, direction.horizontalDistance())));
            poses.translate(-0.5, -0.5, -0.5);
            int shape = entity.form() == GunForm.PLASMA || entity.form() == GunForm.GRENADE ? 1 : 0;
            Minecraft client = Minecraft.getInstance();
            client.getItemRenderer().renderModelLists(client.getModelManager().getModel(MODELS[shape][entity.ammunition().ordinal()]), ItemStack.EMPTY, 0xf000f0,
                    OverlayTexture.NO_OVERLAY, poses, buffers.getBuffer(RenderType.entityTranslucentEmissive(InventoryMenu.BLOCK_ATLAS)));
        }
        poses.popPose();
        super.render(entity, yaw, partialTick, poses, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(FloatingGunProjectile entity) {
        return TEXTURE;
    }
}
