package com.fish_dan_.data_energistics.client.key;

import appeng.api.client.AEKeyRenderHandler;
import appeng.api.stacks.AEKey;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.Level;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;

import java.util.function.Function;

/** Renders an optional Mod's native resource texture while keeping the common fallback self-contained. */
public final class ExternalModKeyRenderHandler<T extends AEKey> implements AEKeyRenderHandler<T> {

    private final Function<T, ResourceLocation> texture;

    public ExternalModKeyRenderHandler(Function<T, ResourceLocation> texture) {
        this.texture = texture;
    }

    @Override
    public void drawInGui(Minecraft minecraft, GuiGraphics guiGraphics, int x, int y, T key) {
        guiGraphics.blit(texture.apply(key), x, y, 0, 0, 16, 16, 16, 16);
    }

    @Override
    public void drawOnBlockFace(PoseStack poseStack, MultiBufferSource buffers, T key, float scale, int light,
                                Level level) {
        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
                .apply(ResourceLocation.fromNamespaceAndPath("data_energistics", "block/key/data"));
        float halfSize = (scale - 0.05F) / 2.0F;
        poseStack.pushPose();
        poseStack.translate(0, 0, 0.01F);
        Matrix4f transform = poseStack.last().pose();
        VertexConsumer buffer = buffers.getBuffer(RenderType.cutout());
        buffer.addVertex(transform, -halfSize, -halfSize, 0).setColor(0xFFFFFFFF)
                .setUv(sprite.getU0(), sprite.getV1()).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                .setNormal(0, 0, 1);
        buffer.addVertex(transform, halfSize, -halfSize, 0).setColor(0xFFFFFFFF)
                .setUv(sprite.getU1(), sprite.getV1()).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                .setNormal(0, 0, 1);
        buffer.addVertex(transform, halfSize, halfSize, 0).setColor(0xFFFFFFFF)
                .setUv(sprite.getU1(), sprite.getV0()).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                .setNormal(0, 0, 1);
        buffer.addVertex(transform, -halfSize, halfSize, 0).setColor(0xFFFFFFFF)
                .setUv(sprite.getU0(), sprite.getV0()).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                .setNormal(0, 0, 1);
        poseStack.popPose();
    }

    @Override
    public Component getDisplayName(T key) {
        return key.getDisplayName();
    }
}
