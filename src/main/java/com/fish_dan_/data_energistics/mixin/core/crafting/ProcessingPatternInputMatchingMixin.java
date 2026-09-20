package com.fish_dan_.data_energistics.mixin.core.crafting;

import com.fish_dan_.data_energistics.common.crafting.pattern.matching.EncodedPatternMatching;
import com.fish_dan_.data_energistics.common.crafting.pattern.matching.ProcessingPatternInputs;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.IPatternDetails.PatternInputSink;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.pattern.AEProcessingPattern;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Leaves unmarked patterns exact and exposes explicitly authorized alternatives to the native crafting planner. */
@Mixin(AEProcessingPattern.class)
abstract class ProcessingPatternInputMatchingMixin {

    @ModifyReturnValue(method = "getInputs", at = @At("RETURN"))
    private IPatternDetails.IInput[] applyModes(IPatternDetails.IInput[] original) {
        return ProcessingPatternInputs.apply(((IPatternDetails) this).getDefinition(), original);
    }

    @Inject(method = "pushInputsToExternalInventory", at = @At("HEAD"), cancellable = true)
    private void emitActualInputs(KeyCounter[] inputs, PatternInputSink sink, CallbackInfo ci) {
        var definition = ((IPatternDetails) this).getDefinition();
        if (!EncodedPatternMatching.flexible(definition)) return;
        ProcessingPatternInputs.push(definition, inputs, sink);
        ci.cancel();
    }
}
