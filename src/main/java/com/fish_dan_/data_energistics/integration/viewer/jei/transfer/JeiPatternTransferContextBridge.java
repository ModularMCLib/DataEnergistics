package com.fish_dan_.data_energistics.integration.viewer.jei.transfer;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.integration.viewer.xei.transfer.PatternEncodingViewerContext;
import com.fish_dan_.data_energistics.integration.viewer.xei.transfer.PatternProviderViewerWorkstations;
import com.fish_dan_.data_energistics.integration.viewer.xei.transfer.ViewerRecipeIdentity;
import com.fish_dan_.data_energistics.menu.patternencoding.PatternEncodingRankingContext;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import it.unimi.dsi.fastutil.objects.ObjectList;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import org.jspecify.annotations.Nullable;

/**
 * Resolves the stable JEI recipe type used by the server-owned provider lookup.
 */
public final class JeiPatternTransferContextBridge {

    private static final ResourceLocation WORKSTATION_SOURCE_ID = Data_Energistics.id(
            "jei_recipe_type_workstations");

    private JeiPatternTransferContextBridge() {}

    /**
     * Registers the workstation lookup owned by the current JEI runtime.
     */
    public static void registerWorkstationSource(PatternProviderViewerWorkstations.Source source) {
        PatternProviderViewerWorkstations.register(WORKSTATION_SOURCE_ID, source);
    }

    /**
     * Detaches the workstation lookup when the JEI runtime is released.
     */
    public static void unregisterWorkstationSource() {
        PatternProviderViewerWorkstations.unregister(WORKSTATION_SOURCE_ID);
    }

    /**
     * Resolves the recipe type directly from the transferred JEI layout.
     */
    public static PatternEncodingRankingContext resolve(IRecipeLayoutDrawable<?> recipeLayout) {
        var recipeType = recipeLayout.getRecipeCategory().getRecipeType();
        return PatternEncodingViewerContext.fromRecipeType(recipeType.getUid());
    }

    /** Resolves the stable recipe identity represented by the transferred JEI layout, if it exposes one. */
    public static @Nullable ResourceLocation resolveRecipeId(IRecipeLayoutDrawable<?> recipeLayout) {
        var level = Minecraft.getInstance().level;
        return ViewerRecipeIdentity.resolve(recipeLayout.getRecipe(),
                level == null ? ObjectList.of() : level.getRecipeManager().getRecipes());
    }
}
