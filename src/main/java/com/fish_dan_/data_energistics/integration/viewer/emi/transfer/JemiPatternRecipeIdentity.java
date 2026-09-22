package com.fish_dan_.data_energistics.integration.viewer.emi.transfer;

import com.fish_dan_.data_energistics.integration.viewer.xei.transfer.ViewerRecipeIdentity;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.jemi.JemiRecipe;
import it.unimi.dsi.fastutil.objects.ObjectList;
import org.jspecify.annotations.Nullable;

/** Loaded only with JEI present, keeping its optional JEMI classes out of the EMI-only transfer path. */
final class JemiPatternRecipeIdentity {

    private JemiPatternRecipeIdentity() {}

    static @Nullable ResourceLocation resolve(EmiRecipe recipe) {
        if (!(recipe instanceof JemiRecipe<?> jemi)) return recipe.getId();
        var level = Minecraft.getInstance().level;
        var nativeId = ViewerRecipeIdentity.resolve(jemi.recipe,
                level == null ? ObjectList.of() : level.getRecipeManager().getRecipes(),
                level == null ? null : level.registryAccess());
        return nativeId == null ? jemi.originalId : nativeId;
    }
}
