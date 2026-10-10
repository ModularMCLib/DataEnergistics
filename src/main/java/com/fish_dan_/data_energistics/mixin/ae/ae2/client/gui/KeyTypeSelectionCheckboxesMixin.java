package com.fish_dan_.data_energistics.mixin.ae.ae2.client.gui;

import com.fish_dan_.data_energistics.client.screen.ae2.KeyTypeSelectionLayout;

import appeng.api.stacks.AEKeyType;
import appeng.client.Point;
import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.ICompositeWidget;
import appeng.client.gui.widgets.AECheckbox;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.renderer.Rect2i;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.function.Consumer;

/**
 * Adds mouse-wheel scrolling to AE2's key-type checkbox list once the registry reaches the overflow threshold.
 *
 * <p>
 * All checkboxes remain registered with the vanilla screen so their existing selection and packet behavior is kept.
 * Rows outside the viewport are hidden and repositioned after the wheel changes the offset.
 * </p>
 */
@Mixin(targets = "appeng.client.gui.implementations.KeyTypeSelectionScreen$KeyTypeCheckboxes", remap = false)
public abstract class KeyTypeSelectionCheckboxesMixin implements ICompositeWidget {

    @Shadow
    private Rect2i bounds;

    @Shadow
    @Final
    private Map<AEKeyType, AECheckbox> checkboxes;

    @Unique
    private int dataEnergistics$originX;

    @Unique
    private int dataEnergistics$originY;

    @Unique
    private int dataEnergistics$viewportHeight;

    @Unique
    private int dataEnergistics$visibleRows;

    @Unique
    private int dataEnergistics$scrollOffset;

    @Unique
    private int dataEnergistics$maxScroll;

    @Inject(method = "populateScreen", at = @At("TAIL"))
    private void dataEnergistics$initializeViewport(Consumer<AbstractWidget> addWidget, Rect2i bounds,
                                                    AEBaseScreen<?> screen, CallbackInfo ci) {
        this.dataEnergistics$originX = bounds.getX();
        this.dataEnergistics$originY = bounds.getY();
        this.dataEnergistics$updateViewport();
    }

    @Override
    public void updateBeforeRender() {
        if (this.checkboxes.isEmpty()) {
            return;
        }

        this.dataEnergistics$updateViewport();
        if (this.dataEnergistics$maxScroll <= 0) {
            return;
        }

        int scroll = this.dataEnergistics$scrollOffset;
        int row = 0;
        for (var checkbox : this.checkboxes.values()) {
            int y = this.dataEnergistics$originY + this.bounds.getY() + row * KeyTypeSelectionLayout.KEY_TYPE_SPACING - scroll * KeyTypeSelectionLayout.KEY_TYPE_SPACING;
            boolean visible = row >= scroll && row < scroll + this.dataEnergistics$visibleRows;
            checkbox.setX(this.dataEnergistics$originX + this.bounds.getX());
            checkbox.setY(y);
            checkbox.visible = visible;
            if (!visible) {
                checkbox.setFocused(false);
            }
            // AE2's parent screen sets active=false for the last selected entry. Preserve that state while hiding rows.
            checkbox.active = visible && checkbox.active;
            row++;
        }
    }

    @Inject(method = "getBounds", at = @At("RETURN"), cancellable = true)
    private void dataEnergistics$expandViewportBounds(CallbackInfoReturnable<Rect2i> cir) {
        if (this.dataEnergistics$maxScroll <= 0) {
            return;
        }

        Rect2i original = cir.getReturnValue();
        cir.setReturnValue(new Rect2i(
                original.getX(),
                original.getY(),
                original.getWidth(),
                this.dataEnergistics$viewportHeight));
    }

    @Override
    public boolean onMouseWheel(Point mousePos, double delta) {
        if (this.dataEnergistics$maxScroll <= 0 || delta == 0) {
            return false;
        }

        int direction = delta > 0 ? -1 : 1;
        this.dataEnergistics$scrollOffset = Math.clamp(
                this.dataEnergistics$scrollOffset + direction, 0, this.dataEnergistics$maxScroll);
        return true;
    }

    @Override
    public boolean wantsAllMouseWheelEvents() {
        return this.dataEnergistics$maxScroll > 0;
    }

    @Unique
    private void dataEnergistics$updateViewport() {
        int keyTypeCount = this.checkboxes.size();
        if (KeyTypeSelectionLayout.requiresScroll(keyTypeCount)) {
            int imageHeight = KeyTypeSelectionLayout.cappedHeight(
                    KeyTypeSelectionLayout.contentHeight(keyTypeCount));
            this.dataEnergistics$viewportHeight = KeyTypeSelectionLayout.viewportHeight(imageHeight);
            this.dataEnergistics$visibleRows = KeyTypeSelectionLayout.visibleRows(imageHeight);
            this.dataEnergistics$maxScroll = Math.max(0, this.checkboxes.size() - this.dataEnergistics$visibleRows);
            this.dataEnergistics$scrollOffset = Math.clamp(
                    this.dataEnergistics$scrollOffset, 0, this.dataEnergistics$maxScroll);
        }
    }
}
