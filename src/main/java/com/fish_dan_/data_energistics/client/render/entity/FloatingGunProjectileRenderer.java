package com.fish_dan_.data_energistics.client.render.entity;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.client.key.AeKeyRenderers;
import com.fish_dan_.data_energistics.common.weapon.platform.model.GunAmmunition;
import com.fish_dan_.data_energistics.common.weapon.platform.model.GunForm;
import com.fish_dan_.data_energistics.common.weapon.platform.projectile.FloatingGunProjectile;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ModelEvent;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import java.util.Locale;

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
            var vertices = buffers.getBuffer(RenderType.lightning());
            var axis = direction.normalize();
            var side = axis.cross(Math.abs(axis.y) < 0.95 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0)).normalize();
            var up = axis.cross(side);
            var matrix = poses.last().pose();
            for (int shell = 0; shell < 2; shell++) {
                double radius = shell == 0 ? 0.14 : 0.045;
                int shade = shell == 0 ? 0x60000000 | color : 0xff000000 | color;
                for (int edge = 0; edge < 8; edge++) {
                    double angle = edge * Math.PI / 4, nextAngle = (edge + 1) * Math.PI / 4;
                    var first = side.scale(Math.cos(angle) * radius).add(up.scale(Math.sin(angle) * radius));
                    var second = side.scale(Math.cos(nextAngle) * radius).add(up.scale(Math.sin(nextAngle) * radius));
                    vertices.addVertex(matrix, (float) first.x, (float) first.y, (float) first.z).setColor(shade);
                    vertices.addVertex(matrix, (float) second.x, (float) second.y, (float) second.z).setColor(shade);
                    vertices.addVertex(matrix, (float) (second.x + direction.x), (float) (second.y + direction.y), (float) (second.z + direction.z)).setColor(shade);
                    vertices.addVertex(matrix, (float) (first.x + direction.x), (float) (first.y + direction.y), (float) (first.z + direction.z)).setColor(shade);
                }
            }
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
