package com.fish_dan_.data_energistics.mixin.viewer.jei;

import com.fish_dan_.data_energistics.integration.viewer.jei.ui.PlayerTerminalJeiGhostHandler;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.common.config.IClientToggleState;
import mezz.jei.gui.ghost.GhostIngredientDrag;
import mezz.jei.gui.ghost.GhostIngredientDragManager;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Reuses JEI's live drag without replacing its input or drop lifecycle. */
@NullMarked
@Mixin(value = GhostIngredientDragManager.class, remap = false)
public abstract class PlayerTerminalGhostPreviewMixin {

    @Shadow
    @Final
    private IClientToggleState toggleState;

    @Shadow
    private @Nullable GhostIngredientDrag<?> ghostIngredientDrag;

    @Shadow
    private @Nullable ITypedIngredient<?> hoveredIngredient;

    @Inject(method = "drawOnForeground", at = @At("TAIL"))
    private void dataEnergistics$previewAboveTerminal(GuiGraphics guiGraphics, int mouseX, int mouseY, CallbackInfo ci) {
        var screen = Minecraft.getInstance().screen;
        if (screen == null) return;
        var drag = ghostIngredientDrag;
        if (drag != null) {
            PlayerTerminalJeiGhostHandler.preview(screen, drag.getIngredient(), mouseX, mouseY, foreground -> drag.drawItem(foreground, mouseX, mouseY));
        } else if (hoveredIngredient != null && !toggleState.isCheatItemsEnabled()) {
            PlayerTerminalJeiGhostHandler.preview(screen, hoveredIngredient, mouseX, mouseY, null);
        }
    }
}
