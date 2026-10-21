package com.fish_dan_.data_energistics.mixin.viewer.jei;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.client.preferences.PatternEncodingPreferencesClient;
import com.fish_dan_.data_energistics.client.terminal.TerminalOverlayClient;
import com.fish_dan_.data_energistics.common.menu.patternencoding.PatternEncodingRankingContext;
import com.fish_dan_.data_energistics.common.menu.patternencoding.source.PatternEncodingSourceHelper;
import com.fish_dan_.data_energistics.integration.viewer.jei.transfer.JeiPatternTransferContextBridge;
import com.fish_dan_.data_energistics.integration.viewer.jei.transfer.PlayerTerminalJeiRecipeTransfer;
import com.fish_dan_.data_energistics.integration.viewer.xei.transfer.PatternEncodingViewerContext;

import appeng.menu.me.items.PatternEncodingTermMenu;
import appeng.parts.encoding.EncodingMode;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferManager;
import mezz.jei.common.transfer.RecipeTransferErrorInternal;
import mezz.jei.common.transfer.RecipeTransferService;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Optional;

/**
 * Captures the exact JEI recipe type around the real transfer handler call.
 */
@Mixin(value = RecipeTransferService.class, remap = false)
public abstract class RecipeTransferServiceMixin {

    @Shadow
    @Final
    private IRecipeTransferManager recipeTransferManager;

    @WrapMethod(method = "transferRecipe(Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;Lmezz/jei/api/gui/IRecipeLayoutDrawable;Lnet/minecraft/world/entity/player/Player;ZZ)Ljava/util/Optional;")
    private <C extends AbstractContainerMenu, R> Optional<IRecipeTransferError> dataEnergistics$captureTransferContext(
                                                                                                                       AbstractContainerScreen<C> screen,
                                                                                                                       IRecipeLayoutDrawable<R> recipeLayout,
                                                                                                                       Player player,
                                                                                                                       boolean maxTransfer,
                                                                                                                       boolean doTransfer,
                                                                                                                       Operation<Optional<IRecipeTransferError>> original) {
        var hostHandler = recipeTransferManager.getRecipeTransferHandler(screen.getMenu(), recipeLayout.getRecipeCategory());
        if (TerminalOverlayClient.canSupplyRecipe(screen.getMenu()) && hostHandler.isPresent()) {
            var targets = PlayerTerminalJeiRecipeTransfer.prepareHost(screen.getMenu(), recipeLayout, hostHandler.get());
            if (targets != null) {
                if (doTransfer) {
                    if (TerminalOverlayClient.encodingWorkspace(screen.getMenu()) != null)
                        PlayerTerminalJeiRecipeTransfer.transfer(screen.getMenu(), recipeLayout, true);
                    TerminalOverlayClient.supplyRecipe(screen.getMenu(), targets);
                }
                return Optional.empty();
            }
        }
        if (TerminalOverlayClient.encodingWorkspace(screen.getMenu()) != null) {
            if (hostHandler.isEmpty()) return PlayerTerminalJeiRecipeTransfer.transfer(screen.getMenu(), recipeLayout, doTransfer);
            if (doTransfer) PlayerTerminalJeiRecipeTransfer.transfer(screen.getMenu(), recipeLayout, true);
        }
        if (!doTransfer || !(screen.getMenu() instanceof PatternEncodingTermMenu menu)) {
            return original.call(screen, recipeLayout, player, maxTransfer, doTransfer);
        }
        PatternEncodingRankingContext rankingContext;
        try {
            rankingContext = JeiPatternTransferContextBridge.resolve(recipeLayout);
        } catch (RuntimeException exception) {
            Data_Energistics.LOGGER.error(
                    "Rejected JEI pattern transfer because its recipe-type context could not be resolved",
                    exception);
            PatternEncodingPreferencesClient.clearTransferredRecipeContext(menu);
            return Optional.of(RecipeTransferErrorInternal.INSTANCE);
        }
        Optional<IRecipeTransferError> result = original.call(screen, recipeLayout, player, maxTransfer, true);
        if (result.isEmpty()) {
            Recipe<?> recipe = recipeLayout.getRecipe() instanceof RecipeHolder<?> holder ? holder.value() :
                    recipeLayout.getRecipe() instanceof Recipe<?> value ? value : null;
            EncodingMode transferMode = PatternEncodingViewerContext.resolveEncodingMode(recipe, false);
            PatternEncodingSourceHelper.rememberTransferSource(menu, transferMode, rankingContext);
            if (transferMode == EncodingMode.PROCESSING) {
                PatternEncodingPreferencesClient.captureTransferredProcessingRecipe(
                        menu,
                        rankingContext,
                        JeiPatternTransferContextBridge.resolveRecipeId(recipeLayout));
            } else {
                PatternEncodingPreferencesClient.captureTransferredRecipe(menu, transferMode);
            }
        }
        return result;
    }
}
