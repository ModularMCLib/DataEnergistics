package com.fish_dan_.data_energistics.client.key;

import com.fish_dan_.data_energistics.integration.magic.astral.AstralSorceryKey;

import appeng.api.client.AEKeyRenderHandler;
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
import hellfirepvp.astralsorcery.client.lib.TexturesAS;
import hellfirepvp.astralsorcery.client.util.RenderConstellationUtil;
import hellfirepvp.astralsorcery.common.constellation.BaseConstellation;
import hellfirepvp.astralsorcery.common.util.data.Vector3;
import org.joml.Matrix4f;

import java.util.List;

/** Uses Astral Sorcery's lumen atlas and constellation star-map renderer for AE key previews. */
public final class AstralSorceryKeyRenderHandler implements AEKeyRenderHandler<AstralSorceryKey> {

    @Override
    public void drawInGui(Minecraft minecraft, GuiGraphics guiGraphics, int x, int y, AstralSorceryKey key) {
        if (key.getKind() == AstralSorceryKey.Kind.LUMEN) {
            TextureAtlasSprite sprite = minecraft.getModelManager().getAtlas(TexturesAS.ATLAS_LUMEN)
                    .getSprite(key.getRegistryId());
            Blitter.sprite(sprite).dest(x, y, 16, 16).blit(guiGraphics);
            return;
        }
        BaseConstellation constellation = (BaseConstellation) key.getValue();
        RenderConstellationUtil.drawConstellationUI(constellation.getConstellationColor(), constellation,
                guiGraphics.pose(), x, y, 16, 16, 0.5F, () -> 1F, true, false);
    }

    @Override
    public void drawOnBlockFace(PoseStack poseStack, MultiBufferSource buffers, AstralSorceryKey key, float scale,
                                int light, Level level) {
        if (key.getKind() == AstralSorceryKey.Kind.LUMEN) {
            TextureAtlasSprite sprite = Minecraft.getInstance().getModelManager().getAtlas(TexturesAS.ATLAS_LUMEN)
                    .getSprite(key.getRegistryId());
            float halfSize = (scale - 0.05F) / 2.0F;
            poseStack.pushPose();
            poseStack.translate(0.0F, 0.0F, 0.01F);
            var transform = poseStack.last().pose();
            var buffer = buffers.getBuffer(RenderType.cutout());
            drawSpriteQuad(buffer, transform, light, halfSize, sprite);
            poseStack.popPose();
            return;
        }
        RenderConstellationUtil.drawConstellationInWorld((BaseConstellation) key.getValue(), poseStack, buffers,
                new Vector3(0, 0, 0), scale, 0.5F, 1F);
    }

    @Override
    public Component getDisplayName(AstralSorceryKey key) {
        return key.getDisplayName();
    }

    @Override
    public List<Component> getTooltip(AstralSorceryKey key) {
        return List.of(key.getDisplayName());
    }

    private static void drawSpriteQuad(VertexConsumer buffer, Matrix4f transform, int light, float halfSize,
                                       TextureAtlasSprite sprite) {
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
    }
}
