package com.fish_dan_.data_energistics.core.mixins.de.ellpeck.naturesaura.blocks.tiles;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

import de.ellpeck.naturesaura.blocks.tiles.BlockEntityNatureAltar;
import de.ellpeck.naturesaura.recipes.AltarRecipe;
import me.fallenbreath.conditionalmixin.api.annotation.Condition;
import me.fallenbreath.conditionalmixin.api.annotation.Restriction;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Read-only access to the altar's native recipe selection, including its catalyst snapshot.
 * Called only on the server thread while the tile is loaded; does not change aura or crafting state.
 */
@Restriction(require = @Condition("naturesaura"))
@Mixin(value = BlockEntityNatureAltar.class, remap = false)
public interface BlockEntityNatureAltarAccessor {

    /**
     * Returns the native selected recipe, or null when the current catalysts reject the input.
     */
    @Invoker("getRecipeForInput")
    @Nullable
    RecipeHolder<AltarRecipe> dataEnergistics$recipeFor(ItemStack input);
}
