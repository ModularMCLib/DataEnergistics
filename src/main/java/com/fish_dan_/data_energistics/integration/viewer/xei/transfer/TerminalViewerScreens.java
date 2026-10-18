package com.fish_dan_.data_energistics.integration.viewer.xei.transfer;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.integration.viewer.emi.transfer.PlayerTerminalEmiRecipeTransfer;
import com.fish_dan_.data_energistics.integration.viewer.jei.transfer.PlayerTerminalJeiRecipeTransfer;

import net.minecraft.client.gui.screens.Screen;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** Optional viewer types stay behind their integration boundaries while the host overlay is retained. */
@NullMarked
public final class TerminalViewerScreens {

    private TerminalViewerScreens() {}

    public static boolean contains(@Nullable Screen screen) {
        return Data_Energistics.isModLoaded("emi") && PlayerTerminalEmiRecipeTransfer.recipeScreen(screen) ||
                Data_Energistics.isModLoaded("jei") && PlayerTerminalJeiRecipeTransfer.recipeScreen(screen);
    }
}
