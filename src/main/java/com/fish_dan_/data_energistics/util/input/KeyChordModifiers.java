package com.fish_dan_.data_energistics.util.input;

import net.minecraft.client.gui.screens.Screen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.settings.KeyModifier;

import com.mojang.blaze3d.platform.InputConstants;

/** Modifier bits shared by the controls editor, persisted bindings and live input matching. */
public final class KeyChordModifiers {

    public static final int CONTROL = 1;
    public static final int SHIFT = 2;
    public static final int ALT = 4;
    public static final int ALL = CONTROL | SHIFT | ALT;

    private KeyChordModifiers() {}

    @OnlyIn(Dist.CLIENT)
    public static int current() {
        return (Screen.hasControlDown() ? CONTROL : 0) | (Screen.hasShiftDown() ? SHIFT : 0) | (Screen.hasAltDown() ? ALT : 0);
    }

    @OnlyIn(Dist.CLIENT)
    public static int of(KeyModifier modifier) {
        return switch (modifier) {
            case CONTROL -> CONTROL;
            case SHIFT -> SHIFT;
            case ALT -> ALT;
            case NONE -> 0;
        };
    }

    @OnlyIn(Dist.CLIENT)
    public static int withoutKey(int modifiers, InputConstants.Key key) {
        if ((modifiers & ~ALL) != 0) throw new IllegalArgumentException("Unsupported key modifier bits: " + modifiers);
        return key.equals(InputConstants.UNKNOWN) ? 0 : modifiers & ~of(KeyModifier.getKeyModifier(key));
    }

    @OnlyIn(Dist.CLIENT)
    public static KeyModifier primary(int modifiers) {
        if ((modifiers & CONTROL) != 0) return KeyModifier.CONTROL;
        if ((modifiers & SHIFT) != 0) return KeyModifier.SHIFT;
        if ((modifiers & ALT) != 0) return KeyModifier.ALT;
        return KeyModifier.NONE;
    }
}
