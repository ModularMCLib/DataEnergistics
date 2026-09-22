package com.fish_dan_.data_energistics.mixin.magic.naturesaura;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.crafting.RecipeHolder;

import de.ellpeck.naturesaura.blocks.tiles.BlockEntityWoodStand;
import de.ellpeck.naturesaura.recipes.TreeRitualRecipe;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Observes the single controller selected by the native grow event and restores its saved timer.
 * Server-thread only, for a loaded stand reserved by the operation; null fields mean no active ritual.
 */
@Mixin(value = BlockEntityWoodStand.class, remap = false)
public interface WoodStandRitualAccessor {

    /** Current native recipe; does not select or start a recipe. */
    @Accessor("recipe")
    @Nullable
    RecipeHolder<TreeRitualRecipe> dataEnergistics$recipe();

    /** Native tree position, which must equal the operation anchor above. */
    @Accessor("ritualPos")
    @Nullable
    BlockPos dataEnergistics$ritualPosition();

    /** Elapsed native ticks, including the material-consumption boundary. */
    @Accessor("timer")
    int dataEnergistics$timer();

    /** Restores a previously observed native timer after a recoverable structure interruption. */
    @Accessor("timer")
    void dataEnergistics$timer(int timer);

    /** Stops only the operation-owned controller before returning its recorded consumed assets. */
    @Accessor("recipe")
    void dataEnergistics$recipe(@Nullable RecipeHolder<TreeRitualRecipe> recipe);

    /** Clears the tree position together with the recipe during cancellation. */
    @Accessor("ritualPos")
    void dataEnergistics$ritualPosition(@Nullable BlockPos position);

    /** Native structure and material validation; caller must first verify all structure chunks are loaded. */
    @Invoker("isRitualOkay")
    boolean dataEnergistics$isRitualOkay();
}
