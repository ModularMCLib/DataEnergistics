package com.fish_dan_.data_energistics.client.weapon.platform;

import com.fish_dan_.data_energistics.client.weapon.platform.animation.DroneFlightPose;
import com.fish_dan_.data_energistics.client.weapon.platform.model.DroneAirframe;
import com.fish_dan_.data_energistics.client.weapon.platform.model.DroneAirframe.Pivot;
import com.fish_dan_.data_energistics.client.weapon.platform.model.DroneAirframe.Wing;
import com.fish_dan_.data_energistics.common.weapon.platform.flight.FloatingGunFlight;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import org.joml.Quaternionf;
import org.jspecify.annotations.NullMarked;

/** Renders the Blockbench airframe with four articulated wings and four independent rotors. */
@NullMarked
public final class FloatingGunRenderer extends EntityRenderer<FloatingGunFlight> {

    private static final RenderType RENDER_TYPE = RenderType.entityCutout(InventoryMenu.BLOCK_ATLAS);

    public FloatingGunRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(FloatingGunFlight entity, float yaw, float partialTick, PoseStack poses, MultiBufferSource buffers, int light) {
        DroneAirframe airframe = DroneAirframe.forForm(entity.form());
        DroneFlightPose flight = DroneFlightPose.sample(entity.getX() - entity.xo, entity.getY() - entity.yo,
                entity.getZ() - entity.zo, yaw, entity.yRotO);
        double age = entity.tickCount + (double) partialTick;
        VertexConsumer consumer = buffers.getBuffer(RENDER_TYPE);
        poses.pushPose();
        poses.translate(0, entity.getBbHeight() / 2 + Math.sin(age * .08 + entity.getId()) * .025, 0);
        poses.mulPose(Axis.YP.rotationDegrees(180 - yaw));
        poses.scale(.65f, .65f, .65f);
        poses.mulPose(Axis.XP.rotationDegrees(-flight.forwardTilt() * .15f));
        poses.mulPose(Axis.ZP.rotationDegrees(-flight.sideTilt() * .15f));
        poses.translate(-.5, -.5, -.5);
        part(airframe.getBody(), poses, consumer);
        int index = 0;
        for (Wing wing : airframe.getWings()) {
            poses.pushPose();
            pivot(poses, wing.hinge(), 1);
            poses.mulPose(new Quaternionf().rotationZYX(
                    (wing.rest().z() + flight.roll(wing.front())) * Mth.DEG_TO_RAD,
                    (wing.rest().y() + flight.sweep(wing.front(), wing.left())) * Mth.DEG_TO_RAD,
                    (wing.rest().x() + flight.pitch(wing.front(), wing.left())) * Mth.DEG_TO_RAD));
            pivot(poses, wing.hinge(), -1);
            part(wing.model(), poses, consumer);
            poses.pushPose();
            pivot(poses, wing.rotorPivot(), 1);
            float phase = (float) ((age * 52.7 + entity.getId() * 39.0 + index++ * 23.0) % 360);
            poses.mulPose(Axis.YP.rotationDegrees(wing.front() == wing.left() ? phase : -phase));
            pivot(poses, wing.rotorPivot(), -1);
            part(wing.rotor(), poses, consumer);
            poses.popPose();
            poses.popPose();
        }
        poses.popPose();
        super.render(entity, yaw, partialTick, poses, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(FloatingGunFlight entity) {
        return DroneAirframe.forForm(entity.form()).getTexture();
    }

    private static void pivot(PoseStack poses, Pivot pivot, int direction) {
        poses.translate(direction * (pivot.x() / 16.0 + .5), direction * pivot.y() / 16.0,
                direction * (pivot.z() / 16.0 + .5));
    }

    private static void part(ModelResourceLocation model, PoseStack poses, VertexConsumer consumer) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.getItemRenderer().renderModelLists(minecraft.getModelManager().getModel(model), ItemStack.EMPTY,
                LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, poses, consumer);
    }
}
