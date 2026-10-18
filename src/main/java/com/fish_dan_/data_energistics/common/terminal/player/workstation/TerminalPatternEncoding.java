package com.fish_dan_.data_energistics.common.terminal.player.workstation;

import com.fish_dan_.data_energistics.common.crafting.packaged.recipe.PackagedPatternEncoding;
import com.fish_dan_.data_energistics.common.crafting.pattern.EncodedPatternRecipeReference;
import com.fish_dan_.data_energistics.common.terminal.player.connection.TerminalConnection;
import com.fish_dan_.data_energistics.common.terminal.player.persistence.TerminalPlayerState;
import com.fish_dan_.data_energistics.common.terminal.player.storage.TerminalNetworkStorage;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.StorageHelper;
import appeng.core.definitions.AEItems;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Uses AE2's four native pattern encoders; configuration never substitutes for paid blank patterns.
 */
@NullMarked
public final class TerminalPatternEncoding {

    private TerminalPatternEncoding() {}

    /** The removed blank slot survives only as a migration source; never accept new items into it. */
    public static void recoverLegacyBlanks(ServerPlayer player, TerminalPlayerState state) {
        for (TerminalWorkspace page : TerminalWorkspace.values()) {
            var inventory = state.work(page).patterns;
            ItemStack old = inventory.getItem(0);
            if (old.isEmpty()) continue;
            ItemStack remainder = old.copy();
            player.getInventory().add(remainder);
            if (remainder.getCount() != old.getCount()) inventory.setItem(0, remainder);
        }
    }

    public static ItemStack encode(ServerPlayer player, TerminalPlayerState state) {
        TerminalWorkspaceState work = state.work();
        ItemStack encoded = switch (state.workspace()) {
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
                        AEItemKey.of(recipe.value().assemble(new SingleRecipeInput(input), player.registryAccess())), false);
            }
            case ANVIL -> ItemStack.EMPTY;
        };
        if (!encoded.isEmpty() && state.workspace() == TerminalWorkspace.PROCESSING) {
            var context = work.context();
            ItemStack completed = PackagedPatternEncoding.complete(player.serverLevel(), context.recipeType(), context.recipeId(), encoded);
            if (completed == null) return ItemStack.EMPTY;
            encoded = completed;
            EncodedPatternRecipeReference.applyProcessingRecipeMetadata(encoded,
                    work.recordRecipeType() ? context.recipeType() : null, context.recipeId());
        }
        return encoded;
    }

    public static ItemStack preview(ServerPlayer player, TerminalPlayerState state) {
        ItemStack encoded = encode(player, state);
        if (encoded.isEmpty()) return ItemStack.EMPTY;
        var details = PatternDetailsHelper.decodePattern(encoded, player.level());
        if (details == null) return ItemStack.EMPTY;
        GenericStack output = details.getPrimaryOutput();
        return output.what() instanceof AEItemKey item ? item.toStack((int) Math.min(output.amount(), Integer.MAX_VALUE)) : GenericStack.wrapInItemStack(output);
    }

    public static String commit(ServerPlayer player, TerminalPlayerState state, @Nullable TerminalConnection connection) {
        ItemStack encoded = encode(player, state);
        var inventory = state.work().patterns;
        ItemStack existing = inventory.getItem(1);
        if (encoded.isEmpty()) {
            if (PatternDetailsHelper.isEncodedPattern(existing)) inventory.setItem(1, AEItems.BLANK_PATTERN.stack(existing.getCount()));
            return "invalid_recipe";
        }
        if (!existing.isEmpty() && (existing.getCount() != 1 ||
                !PatternDetailsHelper.isEncodedPattern(existing) && !AEItems.BLANK_PATTERN.is(existing)))
            return "inventory_full";
        if (existing.isEmpty()) {
            if (connection == null) return "offline";
            AEItemKey blank = AEItemKey.of(AEItems.BLANK_PATTERN);
            long extracted = StorageHelper.poweredExtraction(connection.grid().getEnergyService(), connection.storage(), blank, 1, connection.source());
            TerminalNetworkStorage.checkAmount(extracted, 1);
            if (extracted == 0) return "missing_pattern";
            state.returns().extracted(connection.frequency(), blank, 1);
            inventory.setItem(1, encoded);
            state.returns().delivered(connection.frequency(), blank, 1);
        } else inventory.setItem(1, encoded);
        state.changed();
        return "encoded";
    }
}
