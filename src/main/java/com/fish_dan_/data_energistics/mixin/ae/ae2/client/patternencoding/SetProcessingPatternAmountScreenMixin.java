package com.fish_dan_.data_energistics.mixin.ae.ae2.client.patternencoding;

import com.fish_dan_.data_energistics.client.screen.patternencoding.ProcessingPatternAmountContext;
import com.fish_dan_.data_energistics.client.widget.pattern.ProcessingMatchModeButton;
import com.fish_dan_.data_energistics.menu.patternencoding.PatternOutputMatchMenu;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.client.gui.AESubScreen;
import appeng.client.gui.me.items.PatternEncodingTermScreen;
import appeng.client.gui.me.items.SetProcessingPatternAmountScreen;
import appeng.menu.me.items.PatternEncodingTermMenu;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

/** Adds the encoded pattern's per-slot matching switch to the correct middle-click amount window. */
@Mixin(SetProcessingPatternAmountScreen.class)
public abstract class SetProcessingPatternAmountScreenMixin
                                                            extends AESubScreen<PatternEncodingTermMenu, PatternEncodingTermScreen<PatternEncodingTermMenu>> {

    @Unique
    private ProcessingMatchModeButton dataEnergistics$outputMatchButton;

    protected SetProcessingPatternAmountScreenMixin(
                                                    PatternEncodingTermScreen<PatternEncodingTermMenu> parent) {
        super(parent, "/screens/set_processing_pattern_amount.json");
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void dataEnergistics$addOutputMatchButton(
                                                      PatternEncodingTermScreen<PatternEncodingTermMenu> parentScreen,
                                                      GenericStack currentStack,
                                                      Consumer<GenericStack> setter,
                                                      CallbackInfo ci) {
        ProcessingPatternAmountContext context = (ProcessingPatternAmountContext) parentScreen;
        int inputIndex = context.data_energistics$getProcessingInputAmountTarget();
        int outputIndex = context.data_energistics$getProcessingOutputAmountTarget();
        if ((inputIndex < 0 && outputIndex < 0) || !(currentStack.what() instanceof AEItemKey)) {
            return;
        }

        PatternOutputMatchMenu state = (PatternOutputMatchMenu) this.getMenu();
        this.dataEnergistics$outputMatchButton = new ProcessingMatchModeButton(
                () -> state.data_energistics$getProcessingMatchMode(inputIndex, outputIndex),
                mode -> state.data_energistics$setProcessingMatchMode(inputIndex, outputIndex, mode));
        this.addToLeftToolbar(this.dataEnergistics$outputMatchButton);
    }
}
