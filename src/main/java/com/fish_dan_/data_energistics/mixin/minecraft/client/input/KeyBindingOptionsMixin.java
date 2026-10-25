package com.fish_dan_.data_energistics.mixin.minecraft.client.input;

import com.fish_dan_.data_energistics.util.input.KeyChordMapping;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Options.class)
public abstract class KeyBindingOptionsMixin {

    @Shadow
    public KeyMapping[] keyMappings;
    @Unique
    private boolean dataEnergistics$loading;

    @Inject(method = "load(Z)V", at = @At("HEAD"))
    private void dataEnergistics$beginLoading(boolean limited, CallbackInfo ci) {
        this.dataEnergistics$loading = true;
    }

    @Inject(method = "load(Z)V", at = @At("RETURN"))
    private void dataEnergistics$finishLoading(boolean limited, CallbackInfo ci) {
        this.dataEnergistics$loading = false;
    }

    @Inject(method = "processOptionsForge", at = @At("TAIL"), remap = false)
    private void dataEnergistics$processModifierChords(Options.FieldAccess access, CallbackInfo ci) {
        for (KeyMapping mapping : this.keyMappings) {
            if (mapping instanceof KeyChordMapping chord) chord.processOptions(access, this.dataEnergistics$loading);
        }
    }
}
