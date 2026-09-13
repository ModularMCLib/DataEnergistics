package com.fish_dan_.data_energistics.mixin.client.patternencoding;

import com.fish_dan_.data_energistics.client.screen.patternencoding.ProcessingPatternAmountContext;

import appeng.client.gui.me.common.MEStorageScreen;
import appeng.client.gui.me.items.PatternEncodingTermScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.menu.me.items.PatternEncodingTermMenu;
import appeng.parts.encoding.EncodingMode;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Captures the exact processing slot that opened AE2's amount sub-screen. */
@Mixin(PatternEncodingTermScreen.class)
public abstract class PatternEncodingTermScreenMixin extends MEStorageScreen<PatternEncodingTermMenu>
                                                     implements ProcessingPatternAmountContext {

    @Unique
    private int dataEnergistics$processingInputAmountTarget = -1;
    @Unique
    private int dataEnergistics$processingOutputAmountTarget = -1;

    protected PatternEncodingTermScreenMixin(PatternEncodingTermMenu menu,
                                             Inventory playerInventory,
                                             Component title,
                                             ScreenStyle style) {
        super(menu, playerInventory, title, style);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"))
    private void dataEnergistics$captureProcessingAmountTarget(double xCoord,
                                                               double yCoord,
                                                               int button,
                                                               CallbackInfoReturnable<Boolean> cir) {
        this.dataEnergistics$processingInputAmountTarget = -1;
        this.dataEnergistics$processingOutputAmountTarget = -1;
        if (!Minecraft.getInstance().options.keyPickItem.matchesMouse(button) ||
                this.menu.getMode() != EncodingMode.PROCESSING) {
            return;
        }

        Slot clicked = null;
        for (Slot candidate : this.menu.slots) {
            if (candidate.isActive() && this.isHovering(candidate, xCoord, yCoord)) {
                clicked = candidate;
                break;
            }
        }
        var inputs = this.menu.getProcessingInputSlots();
        for (int index = 0; index < inputs.length; index++) {
            if (clicked == inputs[index]) {
                this.dataEnergistics$processingInputAmountTarget = index;
                return;
            }
        }
        var outputs = this.menu.getProcessingOutputSlots();
        for (int index = 0; index < outputs.length; index++) {
            if (clicked == outputs[index]) {
                this.dataEnergistics$processingOutputAmountTarget = index;
                return;
            }
        }
    }

    @Override
    public int data_energistics$getProcessingInputAmountTarget() {
        return this.dataEnergistics$processingInputAmountTarget;
    }

    @Override
    public int data_energistics$getProcessingOutputAmountTarget() {
        return this.dataEnergistics$processingOutputAmountTarget;
    }
}
