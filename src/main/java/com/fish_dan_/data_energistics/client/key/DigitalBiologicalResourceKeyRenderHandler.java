package com.fish_dan_.data_energistics.client.key;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.ae2.key.BloodKey;
import com.fish_dan_.data_energistics.ae2.key.DigitalBiologicalResourceKey;
import com.fish_dan_.data_energistics.ae2.key.ExperienceKey;

import appeng.api.client.AEKeyRenderHandler;
import appeng.client.gui.style.Blitter;

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

/** Renders shared world resources with a native fluid texture when one is available, otherwise the binary fallback. */
public final class DigitalBiologicalResourceKeyRenderHandler implements AEKeyRenderHandler<DigitalBiologicalResourceKey> {

    private static final float FACE_Z_OFFSET = 0.01F;

    @Override
    public void drawInGui(Minecraft minecraft, GuiGraphics guiGraphics, int x, int y, DigitalBiologicalResourceKey key) {
        Blitter.sprite(sprite(minecraft, key)).dest(x, y, 16, 16).blit(guiGraphics);
    }

    @Override
    public void drawOnBlockFace(PoseStack poseStack, MultiBufferSource buffers, DigitalBiologicalResourceKey key, float scale,
                                int light, Level level) {
        TextureAtlasSprite sprite = sprite(Minecraft.getInstance(), key);
        float halfSize = (scale - 0.05F) / 2.0F;
        poseStack.pushPose();
        poseStack.translate(0.0F, 0.0F, FACE_Z_OFFSET);
        Matrix4f transform = poseStack.last().pose();
        VertexConsumer buffer = buffers.getBuffer(RenderType.cutout());
        addFaceQuad(buffer, transform, light, -halfSize, halfSize, halfSize, -halfSize,
                sprite.getU0(), sprite.getU1(), sprite.getV0(), sprite.getV1());
        poseStack.popPose();
    }

    @Override
    public Component getDisplayName(DigitalBiologicalResourceKey key) {
        return key.getDisplayName();
    }

    private static TextureAtlasSprite sprite(Minecraft minecraft, DigitalBiologicalResourceKey key) {
        ResourceLocation texture = nativeTexture(key);
        if (texture != null) {
            return minecraft.getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(texture);
        }
        return CustomKeyGuiRenderer.dataSprite();
    }

    private static ResourceLocation nativeTexture(DigitalBiologicalResourceKey key) {
        if (key instanceof BloodKey) {
            if (Data_Energistics.isModLoaded("neovitae")) {
                return ResourceLocation.fromNamespaceAndPath("neovitae", "block/essentia_vitae_still");
            }
            if (Data_Energistics.isModLoaded("forbidden_arcanus")) {
                return ResourceLocation.fromNamespaceAndPath("forbidden_arcanus", "block/liquid/blood_still");
            }
        } else if (key instanceof ExperienceKey) {
            if (Data_Energistics.isModLoaded("neovitae")) {
                return ResourceLocation.fromNamespaceAndPath("neovitae", "block/liquified_experience_still");
            }
            if (Data_Energistics.isModLoaded("forbidden_arcanus")) {
                return ResourceLocation.fromNamespaceAndPath("forbidden_arcanus", "block/liquid/experience_still");
            }
        }
        return null;
    }

    private static void addFaceQuad(VertexConsumer buffer, Matrix4f transform, int light, float left, float right,
                                    float top, float bottom, float uLeft, float uRight, float vTop, float vBottom) {
        buffer.addVertex(transform, left, bottom, 0.0F).setColor(0xFFFFFFFF).setUv(uLeft, vBottom)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0.0F, 0.0F, 1.0F);
        buffer.addVertex(transform, right, bottom, 0.0F).setColor(0xFFFFFFFF).setUv(uRight, vBottom)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0.0F, 0.0F, 1.0F);
        buffer.addVertex(transform, right, top, 0.0F).setColor(0xFFFFFFFF).setUv(uRight, vTop)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0.0F, 0.0F, 1.0F);
        buffer.addVertex(transform, left, top, 0.0F).setColor(0xFFFFFFFF).setUv(uLeft, vTop)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0.0F, 0.0F, 1.0F);
    }
}
