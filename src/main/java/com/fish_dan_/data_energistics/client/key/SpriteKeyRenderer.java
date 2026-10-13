package com.fish_dan_.data_energistics.client.key;

import appeng.api.client.AEKeyRenderHandler;
import appeng.api.stacks.AEKey;
import appeng.client.gui.style.Blitter;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;

import java.util.function.Function;

/** Renders the same animated key sprite in GUIs and on block faces using its owning texture atlas. */
class SpriteKeyRenderer<T extends AEKey> implements AEKeyRenderHandler<T> {

    private final Function<T, TextureAtlasSprite> sprite;

    SpriteKeyRenderer(Function<T, TextureAtlasSprite> sprite) {
        this.sprite = sprite;
    }

    @Override
    public void drawInGui(Minecraft minecraft, GuiGraphics guiGraphics, int x, int y, T key) {
        Blitter.sprite(sprite.apply(key)).dest(x, y, 16, 16).blit(guiGraphics);
    }

    @Override
    public void drawOnBlockFace(PoseStack poseStack, MultiBufferSource buffers, T key, float scale, int light, Level level) {
        TextureAtlasSprite keySprite = sprite.apply(key);
        float halfSize = (scale - 0.05F) / 2.0F;
        poseStack.pushPose();
        poseStack.translate(0.0F, 0.0F, 0.01F);
        Matrix4f transform = poseStack.last().pose();
        VertexConsumer buffer = buffers.getBuffer(RenderType.entityCutout(keySprite.atlasLocation()));
        buffer.addVertex(transform, -halfSize, -halfSize, 0.0F).setColor(0xFFFFFFFF).setUv(keySprite.getU0(), keySprite.getV1())
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0.0F, 0.0F, 1.0F);
        buffer.addVertex(transform, halfSize, -halfSize, 0.0F).setColor(0xFFFFFFFF).setUv(keySprite.getU1(), keySprite.getV1())
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0.0F, 0.0F, 1.0F);
        buffer.addVertex(transform, halfSize, halfSize, 0.0F).setColor(0xFFFFFFFF).setUv(keySprite.getU1(), keySprite.getV0())
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0.0F, 0.0F, 1.0F);
        buffer.addVertex(transform, -halfSize, halfSize, 0.0F).setColor(0xFFFFFFFF).setUv(keySprite.getU0(), keySprite.getV0())
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0.0F, 0.0F, 1.0F);
        poseStack.popPose();
    }

    @Override
    public Component getDisplayName(T key) {
        return key.getDisplayName();
    }
}
