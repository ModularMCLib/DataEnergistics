package com.fish_dan_.data_energistics.util.input;

import net.minecraft.client.gui.screens.Screen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Consuming client-thread callback for matching keyboard/mouse presses before ordinary screen
 * input. Temporary listeners must be removed with their owner and must not retain closed screens.
 */
@OnlyIn(Dist.CLIENT)
@FunctionalInterface
public interface ScreenKeyListener {

    /** Receives a non-null screen/key and GUI-scaled pointer; true stops input dispatch. */
    boolean onKeyPressed(Screen screen, SyncedKeyMapping key, double mouseX, double mouseY);
}
