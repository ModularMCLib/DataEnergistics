package com.fish_dan_.data_energistics.recipe;

import com.fish_dan_.data_energistics.recipe.reassembler.DataRipperReassemblerRecipe;
import com.fish_dan_.data_energistics.recipe.reassembler.DataRipperReassemblerRecipeInput;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;

import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.jspecify.annotations.Nullable;

/**
 * Resolves the recipes one processing machine is allowed to execute.
 *
 * <p>
 * The data reassembler and asynchronous factory intentionally use different recipe domains. A resolver owns both input
 * matching and persisted-ID recovery, so the machine does not need a nullable extension hook or a second matching
 * implementation. Optional integrations can therefore be added to the asynchronous resolver without changing the
 * native machine's recipe domain.
 * </p>
 *
 * <p>
 * Implementations are queried on the game thread against the current {@link Level} recipe manager. They must not
 * retain recipe-manager snapshots across a reload, and a returned holder must match the complete input group supplied
 * by the caller. A missing recipe is represented by {@code null} so the machine can try its next input colour or stop
 * the current channel without inventing a fallback recipe.
 * </p>
 */
public interface ProcessingRecipeResolver {

    /**
     * Finds one recipe for the complete current machine input group.
     *
     * @param level             current level and recipe manager; never {@code null}
     * @param input             colour-filtered item, fluid, and key inputs; never {@code null}
     * @param excludedRecipeIds recipe identities already reserved by another channel; never {@code null}
     * @return a matching recipe holder, or {@code null} when this domain has no match
     */
    @Nullable
    RecipeHolder<DataRipperReassemblerRecipe> find(Level level,
                                                   DataRipperReassemblerRecipeInput input,
                                                   ObjectSet<ResourceLocation> excludedRecipeIds);

    /**
     * Recovers one recipe by its persisted identity and validates it against the current input group.
     *
     * @param level    current level and recipe manager; never {@code null}
     * @param recipeId identity stored by a processing channel; never {@code null}
     * @param input    current colour-filtered inputs; never {@code null}
     * @return the current matching holder, or {@code null} when the recipe was removed or no longer matches
     */
    @Nullable
    RecipeHolder<DataRipperReassemblerRecipe> findById(Level level,
                                                       ResourceLocation recipeId,
                                                       DataRipperReassemblerRecipeInput input);
}
