package com.fish_dan_.data_energistics.client.weapon.platform;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.data.DEDataComponents;
import com.fish_dan_.data_energistics.common.data.DEItems;
import com.fish_dan_.data_energistics.common.research.item.ItemResearchUpgrades;
import com.fish_dan_.data_energistics.common.weapon.platform.flight.FloatingGunFlight;
import com.fish_dan_.data_energistics.common.weapon.platform.model.GunForm;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import it.unimi.dsi.fastutil.objects.ObjectImmutableList;

import java.util.EnumMap;

/** Renders the interpolated server flight position using the same baked model as the item and menu. */
public final class FloatingGunRenderer extends EntityRenderer<FloatingGunFlight> {

    private static final ResourceLocation TEXTURE = Data_Energistics.id("textures/item/weapon_platform/floating_gun/panel_mint.png");
    private final EnumMap<GunForm, ItemStack> models = new EnumMap<>(GunForm.class);

    public FloatingGunRenderer(EntityRendererProvider.Context context) {
        super(context);
        for (GunForm form : GunForm.values()) {
            ItemStack gun = new ItemStack(DEItems.DIGITAL_FLOATING_GUN.get());
            if (form != GunForm.NORMAL) gun.set(DEDataComponents.RESEARCH_UPGRADES, new ItemResearchUpgrades(ObjectImmutableList.of(form.researchId)));
            models.put(form, gun);
        }
    }

    @Override
    public void render(FloatingGunFlight entity, float yaw, float partialTick, PoseStack poses, MultiBufferSource buffers, int light) {
        poses.pushPose();
        poses.translate(0, entity.getBbHeight() / 2 + Math.sin((entity.tickCount + partialTick) * 0.08 + entity.getId()) * 0.025, 0);
        poses.mulPose(Axis.YP.rotationDegrees(180 - yaw));
        Minecraft.getInstance().getItemRenderer().renderStatic(models.get(entity.form()), ItemDisplayContext.NONE,
                LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, poses, buffers, entity.level(), entity.getId());
        poses.popPose();
        super.render(entity, yaw, partialTick, poses, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(FloatingGunFlight entity) {
        return TEXTURE;
    }
}
