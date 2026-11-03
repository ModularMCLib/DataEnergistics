package com.fish_dan_.data_energistics.integration.guide.guideme.client;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.model.ModelBakery;

import guideme.document.LytRect;
import guideme.document.block.LytBlock;
import guideme.layout.LayoutContext;
import guideme.render.RenderContext;

/** Displays the animated vanilla fire sprite in the incineration recipe title. */
public final class IncinerationGuideFireIcon extends LytBlock {

    private static final int SIZE = 8;

    @Override
    protected LytRect computeLayout(LayoutContext context, int x, int y, int availableWidth) {
        return new LytRect(x, y, SIZE, SIZE);
    }

    @Override
    protected void onLayoutMoved(int deltaX, int deltaY) {}

    @Override
    public void renderBatch(RenderContext context, MultiBufferSource buffers) {}

    @Override
    public void render(RenderContext context) {
        context.guiGraphics().blit(this.bounds.x(), this.bounds.y(), 0, SIZE, SIZE, ModelBakery.FIRE_0.sprite());
    }
}
