package com.fish_dan_.data_energistics.integration.viewer.xei.recipe.incineration;

import com.fish_dan_.data_energistics.api.recipe.incineration.IncinerationRecipe;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.client.model.data.ModelData;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.math.Axis;

import java.util.List;

/** Shared layout and block scene for the JEI and EMI incineration recipe views. */
public final class IncinerationRecipeDisplay {

    public static final int OUTPUT_X = 122;
    public static final int OUTPUT_Y = 26;
    public static final int ARROW_LEFT_X = 32;
    public static final int ARROW_RIGHT_X = 92;
    public static final int ARROW_Y = 27;
    public static final int CENTER_X = 74;
    public static final int ACTION_Y = 3;
    public static final int SCENE_X = 58;
    public static final int SCENE_Y = 16;
    public static final int SCENE_WIDTH = 32;
    public static final int SCENE_HEIGHT = 50;

    private static final int SLOT_SIZE = 18;
    private static final int INPUT_X = 10;
    private static final int INPUT_Y = 10;
    private static final float BLOCK_SCALE = 18.0F;
    private static final int BASE_BOTTOM_Y = 40;

    private IncinerationRecipeDisplay() {}

    public static int inputX(int index) {
        return INPUT_X + index / 3 * SLOT_SIZE;
    }

    public static int inputY(int inputCount, int index) {
        return INPUT_Y + Math.max(0, 3 - inputCount) * SLOT_SIZE / 2 + index % 3 * SLOT_SIZE;
    }

    public static boolean isSceneHovered(double mouseX, double mouseY) {
        return mouseX >= SCENE_X && mouseX < SCENE_X + SCENE_WIDTH &&
                mouseY >= SCENE_Y && mouseY < SCENE_Y + SCENE_HEIGHT;
    }

    public static List<Component> sceneTooltip(IncinerationRecipe recipe) {
        Component transformation = recipe.getTransformedBlock()
                .map(block -> Component.translatable("recipe.data_energistics.incineration.base.transformed", new ItemStack(block).getHoverName()))
                .orElseGet(() -> Component.translatable("recipe.data_energistics.incineration.base.not_transformed"));
        return List.of(Blocks.FIRE.getName(), recipe.getBaseBlock().getName(), transformation);
    }

    public static void renderScene(GuiGraphics graphics, IncinerationRecipe recipe, int x, int y) {
        graphics.flush();
        var pose = graphics.pose();
        pose.pushPose();
        try {
            pose.translate(x + SCENE_WIDTH / 2.0F, y + BASE_BOTTOM_Y, 150);
            pose.scale(BLOCK_SCALE, -BLOCK_SCALE, BLOCK_SCALE);
            pose.mulPose(Axis.XP.rotationDegrees(30.0F));
            pose.mulPose(Axis.YP.rotationDegrees(45.0F));
            pose.translate(-0.5F, 0.0F, -0.5F);
            Lighting.setupFor3DItems();

            var renderer = Minecraft.getInstance().getBlockRenderer();
            renderer.renderSingleBlock(recipe.getBaseBlock().defaultBlockState(), pose, graphics.bufferSource(),
                    LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
            pose.translate(0.0F, 1.0F, 0.0F);
            renderer.renderSingleBlock(Blocks.FIRE.defaultBlockState(), pose, graphics.bufferSource(),
                    LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
            graphics.flush();
        } finally {
            pose.popPose();
            Lighting.setupFor3DItems();
        }
    }
}
