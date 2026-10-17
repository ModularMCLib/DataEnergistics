package com.fish_dan_.data_energistics.common.terminal.player.workstation;

import com.fish_dan_.data_energistics.common.terminal.player.persistence.TerminalPlayerState;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.core.definitions.AEItems;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

/**
 * Uses AE2's four native pattern encoders; configuration never substitutes for paid blank patterns.
 */
public final class TerminalPatternEncoding {

    private TerminalPatternEncoding() {}

    public static ItemStack encode(ServerPlayer player, TerminalPlayerState state) {
        TerminalWorkspaceState work = state.work();
        return switch (state.workspace()) {
            case PROCESSING -> {
                var inputs = new ObjectArrayList<GenericStack>();
                var outputs = new ObjectArrayList<GenericStack>();
                boolean hasInput = false;
                for (int index = 0; index < work.patternInputs.size(); index++) {
                    var input = work.patternInputs.get(index);
                    inputs.add(input);
                    hasInput |= input != null;
                }
                for (int index = 0; index < work.patternOutputs.size(); index++)
                    outputs.add(work.patternOutputs.get(index));
                yield !hasInput || outputs.getFirst() == null ? ItemStack.EMPTY : PatternDetailsHelper.encodeProcessingPattern(inputs, outputs);
            }
            case CRAFTING -> {
                var input = TerminalWorkstation.craftingInput(work, true).input();
                var recipe = player.serverLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, player.level());
                if (recipe.isEmpty()) yield ItemStack.EMPTY;
                ItemStack[] items = new ItemStack[9];
                for (int index = 0; index < 9; index++) items[index] = TerminalWorkstation.input(work, index, true);
                yield PatternDetailsHelper.encodeCraftingPattern(recipe.get(), items, recipe.get().value().assemble(input, player.registryAccess()), work.substitutes(), work.fluidSubstitutes());
            }
            case SMITHING -> {
                var input = TerminalWorkstation.smithingInput(work, true);
                var recipe = player.serverLevel().getRecipeManager().getRecipeFor(RecipeType.SMITHING, input, player.level());
                if (recipe.isEmpty()) yield ItemStack.EMPTY;
                yield PatternDetailsHelper.encodeSmithingTablePattern(recipe.get(), AEItemKey.of(input.template()), AEItemKey.of(input.base()), AEItemKey.of(input.addition()),
                        AEItemKey.of(recipe.get().value().assemble(input, player.registryAccess())), work.substitutes());
            }
            case STONECUTTING -> {
                var recipes = TerminalWorkstation.stonecuttingRecipes(player, work, true);
                if (recipes.isEmpty()) yield ItemStack.EMPTY;
                var recipe = recipes.get(Math.min(work.recipeIndex(), recipes.size() - 1));
                var input = TerminalWorkstation.input(work, 0, true);
                yield PatternDetailsHelper.encodeStonecuttingPattern(recipe, AEItemKey.of(input),
                        AEItemKey.of(recipe.value().assemble(new SingleRecipeInput(input), player.registryAccess())), work.substitutes());
            }
            case ANVIL -> ItemStack.EMPTY;
        };
    }

    public static String commit(ServerPlayer player, TerminalPlayerState state) {
        ItemStack encoded = encode(player, state);
        if (encoded.isEmpty()) return "invalid_recipe";
        var inventory = state.work().patterns;
        ItemStack blank = inventory.getItem(0);
        ItemStack existing = inventory.getItem(1);
        if (!blank.is(AEItems.BLANK_PATTERN.asItem())) return "missing_pattern";
        if (!existing.isEmpty() && (!ItemStack.isSameItemSameComponents(existing, encoded) || existing.getCount() >= existing.getMaxStackSize()))
            return "inventory_full";
        inventory.removeItem(0, 1);
        if (existing.isEmpty()) inventory.setItem(1, encoded);
        else {
            existing.grow(1);
            inventory.setChanged();
        }
        state.changed();
        return "encoded";
    }
}
