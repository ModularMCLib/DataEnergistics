package com.fish_dan_.data_energistics.integration.guide.guideme.client;

import com.fish_dan_.data_energistics.api.recipe.incineration.IncinerationRecipe;
import com.fish_dan_.data_energistics.integration.viewer.xei.recipe.incineration.IncinerationRecipeDisplay;

import net.minecraft.client.renderer.MultiBufferSource;

import guideme.document.LytRect;
import guideme.document.block.LytBlock;
import guideme.document.interaction.GuideTooltip;
import guideme.document.interaction.InteractiveElement;
import guideme.document.interaction.TextTooltip;
import guideme.layout.LayoutContext;
import guideme.render.RenderContext;

import java.util.Optional;

/** Renders the incineration fire above its actual base block inside a recipe box. */
public final class IncinerationGuideScene extends LytBlock implements InteractiveElement {

    private final IncinerationRecipe recipe;

    public IncinerationGuideScene(IncinerationRecipe recipe) {
        this.recipe = recipe;
    }

    @Override
    protected LytRect computeLayout(LayoutContext context, int x, int y, int availableWidth) {
        return new LytRect(x, y, IncinerationRecipeDisplay.SCENE_WIDTH, IncinerationRecipeDisplay.SCENE_HEIGHT);
    }

    @Override
    protected void onLayoutMoved(int deltaX, int deltaY) {}

    @Override
    public void renderBatch(RenderContext context, MultiBufferSource buffers) {}

    @Override
    public void render(RenderContext context) {
        if (context.intersectsViewport(this.bounds)) {
            IncinerationRecipeDisplay.renderScene(context.guiGraphics(), this.recipe, this.bounds.x(), this.bounds.y());
        }
    }

    @Override
    public Optional<GuideTooltip> getTooltip(float x, float y) {
        return Optional.of(new TextTooltip(IncinerationRecipeDisplay.sceneTooltip(this.recipe)));
    }
}
