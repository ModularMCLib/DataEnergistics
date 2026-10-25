package com.fish_dan_.data_energistics.mixin.minecraft.client.input;

import com.fish_dan_.data_energistics.util.input.KeyChordMapping;

import net.minecraft.client.KeyMapping;

import com.mojang.blaze3d.platform.InputConstants;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(KeyMapping.class)
public abstract class KeyMappingConflictMixin {

    @Inject(method = "set", at = @At("HEAD"))
    private static void dataEnergistics$releaseFullChord(InputConstants.Key key, boolean down, CallbackInfo ci) {
        if (!down) KeyChordMapping.releaseKey(key);
    }

    @Inject(method = "same", at = @At("HEAD"), cancellable = true)
    private void dataEnergistics$compareFullChord(KeyMapping other, CallbackInfoReturnable<Boolean> cir) {
        if (other instanceof KeyChordMapping chord) cir.setReturnValue(chord.same((KeyMapping) (Object) this));
    }
}
