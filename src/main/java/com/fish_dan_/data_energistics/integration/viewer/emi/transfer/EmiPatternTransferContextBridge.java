package com.fish_dan_.data_energistics.integration.viewer.emi.transfer;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.integration.viewer.emi.recipe.DataChargePressEmiRecipe;
import com.fish_dan_.data_energistics.integration.viewer.emi.recipe.DataRipperReassemblerEmiRecipe;
import com.fish_dan_.data_energistics.integration.viewer.xei.transfer.PatternEncodingViewerContext;
import com.fish_dan_.data_energistics.integration.viewer.xei.transfer.PatternProviderViewerWorkstations;
import com.fish_dan_.data_energistics.menu.patternencoding.PatternEncodingRankingContext;

import appeng.menu.me.items.PatternEncodingTermMenu;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.fml.ModList;

import dev.emi.emi.api.recipe.EmiRecipe;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jspecify.annotations.Nullable;

/**
 * Resolves exact EMI category workstations and scopes them to one synchronous transfer call.
 */
public final class EmiPatternTransferContextBridge {

    private static final ResourceLocation WORKSTATION_SOURCE_ID = Data_Energistics.id(
            "emi_recipe_type_workstations");
    private static final ThreadLocal<ObjectArrayList<Frame>> FRAMES = ThreadLocal.withInitial(ObjectArrayList::new);

    private EmiPatternTransferContextBridge() {}

    /**
     * Registers the workstation lookup owned by EMI's current recipe registry.
     */
    public static void registerWorkstationSource(PatternProviderViewerWorkstations.Source source) {
        PatternProviderViewerWorkstations.register(WORKSTATION_SOURCE_ID, source);
    }

    /**
     * Resolves a canonical context directly from the recipe's EMI category ID.
     */
    public static PatternEncodingRankingContext resolve(EmiRecipe recipe) {
        return PatternEncodingViewerContext.fromRecipeType(recipe.getCategory().getId());
    }

    /** Resolves the stable recipe identity represented by the transferred EMI recipe. */
    public static @Nullable ResourceLocation resolveRecipeId(@Nullable RecipeHolder<?> holder, EmiRecipe recipe) {
        if (recipe instanceof DataChargePressEmiRecipe dataChargePressRecipe) {
            return dataChargePressRecipe.patternRecipeId();
        }
        if (recipe instanceof DataRipperReassemblerEmiRecipe dataRipperReassemblerRecipe) {
            return dataRipperReassemblerRecipe.patternRecipeId();
        }
        ResourceLocation resolved = holder != null ? holder.id() :
                ModList.get().isLoaded("jei") ? JemiPatternRecipeIdentity.resolve(recipe) : recipe.getId();
        return canonicalRecipeId(resolved);
    }

    /** Converts EMI's slash-prefixed synthetic id back to the longest matching native recipe id. */
    private static @Nullable ResourceLocation canonicalRecipeId(@Nullable ResourceLocation id) {
        if (id == null || !id.getPath().startsWith("/")) return id;
        var level = Minecraft.getInstance().level;
        if (level != null) {
            String syntheticPath = id.getPath();
            ResourceLocation best = getBest(id, level, syntheticPath);
            if (best != null) return best;
        }
        String path = id.getPath().substring(1);
        return path.isEmpty() ? null : ResourceLocation.fromNamespaceAndPath(id.getNamespace(), path);
    }

    private static @Nullable ResourceLocation getBest(ResourceLocation id, ClientLevel level, String syntheticPath) {
        ResourceLocation best = null;
        for (var candidate : level.getRecipeManager().getRecipes()) {
            ResourceLocation candidateId = candidate.id();
            String prefix = "/" + candidateId.getPath() + "/";
            if (candidateId.getNamespace().equals(id.getNamespace()) && syntheticPath.startsWith(prefix) &&
                    (best == null || candidateId.getPath().length() > best.getPath().length())) {
                best = candidateId;
            }
        }
        return best;
    }

    /**
     * Starts a transfer frame after the viewer context has been validated.
     */
    public static void begin(PatternEncodingTermMenu menu,
                             PatternEncodingRankingContext context) {
        FRAMES.get().add(new Frame(menu, context));
    }

    /**
     * Returns the context scoped to the successful transfer, rejecting an unbalanced callback.
     */
    public static PatternEncodingRankingContext requireCurrent(PatternEncodingTermMenu menu) {
        ObjectArrayList<Frame> frames = FRAMES.get();
        Frame frame = frames.isEmpty() ? null : frames.getLast();
        if (frame == null || frame.menu() != menu) {
            throw new IllegalStateException("EMI transfer context is not scoped to the current menu");
        }
        return frame.context();
    }

    /**
     * Removes a frame only when it belongs to the menu whose transfer just returned.
     */
    public static void end(PatternEncodingTermMenu menu) {
        ObjectArrayList<Frame> frames = FRAMES.get();
        Frame frame = frames.isEmpty() ? null : frames.removeLast();
        if (frame == null) {
            FRAMES.remove();
            throw new IllegalStateException("EMI transfer context ended without an active frame");
        }
        if (frame.menu() != menu) {
            frames.clear();
            FRAMES.remove();
            throw new IllegalStateException("EMI transfer contexts were closed out of order");
        }
        if (frames.isEmpty()) {
            FRAMES.remove();
        }
    }

    private record Frame(PatternEncodingTermMenu menu,
                         PatternEncodingRankingContext context) {}
}
