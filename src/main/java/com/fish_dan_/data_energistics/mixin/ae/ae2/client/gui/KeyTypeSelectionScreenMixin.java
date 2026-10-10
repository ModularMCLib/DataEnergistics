package com.fish_dan_.data_energistics.mixin.ae.ae2.client.gui;

import com.fish_dan_.data_energistics.client.screen.ae2.KeyTypeSelectionLayout;
import com.fish_dan_.data_energistics.mixin.minecraft.accessor.AbstractContainerScreenAccessor;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.implementations.KeyTypeSelectionScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.menu.interfaces.KeyTypeSelectionMenu;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps the AE2 key-type selection dialog inside the available GUI height.
 *
 * <p>
 * The original screen grows its generated background to the complete number of registered key types. The companion
 * checkbox mixin then uses the remaining content area as a scrollable viewport.
 * </p>
 */
@Mixin(value = KeyTypeSelectionScreen.class, remap = false)
public abstract class KeyTypeSelectionScreenMixin {

    @Inject(method = "setHeight", at = @At("HEAD"), cancellable = true)
    private void dataEnergistics$capHeight(int height, CallbackInfo ci) {
        int keyTypeCount = ((KeyTypeSelectionMenu) ((AEBaseScreen<?>) (Object) this).getMenu())
                .getClientKeyTypeSelection().keyTypes().size();
        if (KeyTypeSelectionLayout.requiresScroll(keyTypeCount)) {
            ScreenStyle style = ((AEBaseScreen<?>) (Object) this).getStyle();
            int contentHeight = KeyTypeSelectionLayout.contentHeight(keyTypeCount);
            int cappedHeight = KeyTypeSelectionLayout.cappedHeight(Math.min(height, contentHeight));
            var generatedBackground = style.getGeneratedBackground();
            if (generatedBackground != null) {
                generatedBackground.setHeight(cappedHeight);
            }
            ((AbstractContainerScreenAccessor) this).dataEnergistics$setImageHeight(cappedHeight);
            ci.cancel();
        }
    }
}
