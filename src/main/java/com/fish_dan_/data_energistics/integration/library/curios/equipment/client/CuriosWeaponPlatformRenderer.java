package com.fish_dan_.data_energistics.integration.library.curios.equipment.client;

import com.fish_dan_.data_energistics.common.data.DEItems;
import com.fish_dan_.data_energistics.common.weapon.platform.equipment.EquippedWeaponPlatform;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import com.mojang.blaze3d.vertex.PoseStack;
import org.jspecify.annotations.NullMarked;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.client.CuriosRendererRegistry;
import top.theillusivec4.curios.api.client.ICurioRenderer;

/** Uses vanilla's head transform and the item's HEAD display, exactly as the helmet slot does. */
@NullMarked
public final class CuriosWeaponPlatformRenderer implements ICurioRenderer {

    public static void register() {
        CuriosRendererRegistry.register(DEItems.DIGITAL_WEAPON_PLATFORM.get(), CuriosWeaponPlatformRenderer::new);
    }

    @Override
    public <T extends LivingEntity, M extends EntityModel<T>> void render(ItemStack stack, SlotContext context, PoseStack poses,
                                                                          RenderLayerParent<T, M> parent, MultiBufferSource buffers, int light,
                                                                          float limbSwing, float limbSwingAmount, float partialTick,
                                                                          float ageInTicks, float netHeadYaw, float headPitch) {
        if (!context.visible() || !context.identifier().equals("head") || !(context.entity() instanceof Player player) || EquippedWeaponPlatform.find(player) != stack || !(parent.getModel() instanceof HumanoidModel<?> model)) return;
        poses.pushPose();
        model.head.translateAndRotate(poses);
        CustomHeadLayer.translateToHead(poses, false);
        Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.HEAD, light, OverlayTexture.NO_OVERLAY,
                poses, buffers, player.level(), player.getId());
        poses.popPose();
    }
}
