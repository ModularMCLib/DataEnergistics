package com.fish_dan_.data_energistics.mixin.minecraft.client.input;

import com.fish_dan_.data_energistics.util.input.KeyChordMapping;
import com.fish_dan_.data_energistics.util.input.KeyChordModifiers;

import net.minecraft.Util;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.options.controls.KeyBindsList;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.neoforged.neoforge.client.settings.KeyModifier;

import com.mojang.blaze3d.platform.InputConstants;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(KeyBindsScreen.class)
public abstract class KeyBindingEditorMixin {

    @Shadow public @Nullable KeyMapping selectedKey;
    @Shadow public long lastKeySelection;
    @Shadow private KeyBindsList keyBindsList;
    @Unique private @Nullable KeyMapping dataEnergistics$editing;
    @Unique private InputConstants.Key dataEnergistics$modifierKey = InputConstants.UNKNOWN;
    @Unique private int dataEnergistics$modifiers;

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void dataEnergistics$recordChord(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        if (!(this.selectedKey instanceof KeyChordMapping chord)) return;
        dataEnergistics$beginCapture(chord);
        InputConstants.Key key = InputConstants.getKey(keyCode, scanCode);
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) dataEnergistics$finish(chord, InputConstants.UNKNOWN, 0);
        else if (KeyModifier.isKeyCodeModifier(key)) {
            this.dataEnergistics$modifierKey = key;
            this.dataEnergistics$modifiers = KeyChordModifiers.withoutKey(KeyChordModifiers.current(), key);
        } else dataEnergistics$finish(chord, key, KeyChordModifiers.current());
        cir.setReturnValue(true);
    }

    @Inject(method = "keyReleased", at = @At("HEAD"), cancellable = true)
    private void dataEnergistics$recordModifierKey(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        if (!(this.selectedKey instanceof KeyChordMapping chord)) return;
        if (this.dataEnergistics$editing == chord && this.dataEnergistics$modifierKey.equals(InputConstants.getKey(keyCode, scanCode))) {
            dataEnergistics$finish(chord, this.dataEnergistics$modifierKey, this.dataEnergistics$modifiers);
        }
        cir.setReturnValue(true);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void dataEnergistics$recordMouseChord(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (!(this.selectedKey instanceof KeyChordMapping chord)) return;
        dataEnergistics$finish(chord, InputConstants.Type.MOUSE.getOrCreate(button), KeyChordModifiers.current());
        cir.setReturnValue(true);
    }

    @Unique
    private void dataEnergistics$beginCapture(KeyMapping mapping) {
        if (this.dataEnergistics$editing == mapping) return;
        this.dataEnergistics$editing = mapping;
        this.dataEnergistics$modifierKey = InputConstants.UNKNOWN;
        this.dataEnergistics$modifiers = 0;
    }

    @Unique
    private void dataEnergistics$finish(KeyChordMapping chord, InputConstants.Key key, int modifiers) {
        chord.setChord(key, modifiers);
        this.selectedKey = null;
        this.dataEnergistics$editing = null;
        this.lastKeySelection = Util.getMillis();
        Minecraft.getInstance().options.setKey(chord, key);
        this.keyBindsList.resetMappingAndUpdateButtons();
    }
}
