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
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import org.joml.Matrix4f;

import java.util.function.Function;

/** Renders the same animated key sprite in GUIs and on block faces using its owning texture atlas. */
class SpriteKeyRenderer<T extends AEKey> implements AEKeyRenderHandler<T> {

    private final Function<T, TextureAtlasSprite> sprite;
    private final Reference2IntOpenHashMap<TextureAtlasSprite> colors = new Reference2IntOpenHashMap<>();

    SpriteKeyRenderer(Function<T, TextureAtlasSprite> sprite) {
        this.sprite = sprite;
    }

    int color(T key) {
        TextureAtlasSprite current = sprite.apply(key);
        if (colors.containsKey(current)) return colors.getInt(current);
        TextureAtlasSprite image = current;
            double red = 0, green = 0, blue = 0, total = 0;
            for (int x = 0; x < image.contents().width(); x++) for (int y = 0; y < image.contents().height(); y++) {
                int pixel = image.getPixelRGBA(0, x, y);
                int r = pixel & 255, g = pixel >> 8 & 255, b = pixel >> 16 & 255;
                double weight = (pixel >>> 24) / 255.0 * (1 + Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b)));
                red += r * weight; green += g * weight; blue += b * weight; total += weight;
            }
        int result = total == 0 ? 0xffffff : (int) (red / total) << 16 | (int) (green / total) << 8 | (int) (blue / total);
        colors.put(current, result);
        return result;
    }

    void clearColors() { colors.clear(); }

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
