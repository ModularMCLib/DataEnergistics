package com.fish_dan_.data_energistics.integration.magic.naturesaura.packaged;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.crafting.RecipeHolder;

import de.ellpeck.naturesaura.recipes.TreeRitualRecipe;
import org.jspecify.annotations.Nullable;

/**
 * Observes the single controller selected by the native grow event and restores its saved timer.
 * Server-thread only, for a loaded stand reserved by the operation; null fields mean no active ritual.
 */
public interface BlockEntityWoodStandAccessor {

    /** Current native recipe; does not select or start a recipe. */
    @Nullable
    RecipeHolder<TreeRitualRecipe> dataEnergistics$recipe();

    /** Native tree position, which must equal the operation anchor above. */
    @Nullable
    BlockPos dataEnergistics$ritualPosition();

    /** Elapsed native ticks, including the material-consumption boundary. */
    int dataEnergistics$timer();

    /** Restores a previously observed native timer after a recoverable structure interruption. */
    void dataEnergistics$timer(int timer);

    /** Stops only the operation-owned controller before returning its recorded consumed assets. */
    void dataEnergistics$recipe(@Nullable RecipeHolder<TreeRitualRecipe> recipe);

    /** Clears the tree position together with the recipe during cancellation. */
    void dataEnergistics$ritualPosition(@Nullable BlockPos position);

    /** Native structure and material validation; caller must first verify all structure chunks are loaded. */
    boolean dataEnergistics$isRitualOkay();
}
