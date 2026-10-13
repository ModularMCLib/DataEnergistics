package com.fish_dan_.data_energistics.integration.ae.ae2wtlib.terminal;

import com.fish_dan_.data_energistics.integration.MOD;
import com.fish_dan_.data_energistics.integration.ae.ae2wtlib.terminal.client.Ae2WtLibClientCompat;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

public final class Ae2WtLibCompat {

    private Ae2WtLibCompat() {}

    @SuppressWarnings("unchecked")
    public static <T> T maybeReplaceWirelessPatternEncodingScreen(Object currentScreen, boolean applyImmediately) {
        if (FMLEnvironment.dist != Dist.CLIENT || currentScreen == null || !MOD.isAe2WtLibLoaded()) {
            return null;
        }

        return (T) Ae2WtLibClientCompat.maybeReplaceWirelessPatternEncodingScreen(currentScreen, applyImmediately);
    }
}
