package com.fish_dan_.data_energistics.common.terminal.player.workstation.crafting;

import com.fish_dan_.data_energistics.common.terminal.player.encoding.TerminalPatternContext;
import com.fish_dan_.data_energistics.common.terminal.player.workstation.TerminalWorkspaceState;
import com.fish_dan_.data_energistics.common.terminal.player.workstation.TerminalWorkstation;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameRules;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import org.jspecify.annotations.NullMarked;

import java.util.Comparator;
import java.util.Optional;

/** One authoritative set of matching recipes shared by result selection, crafting and AE encoding. */
@NullMarked
public record TerminalCraftingRecipes(CraftingInput input, ObjectList<RecipeHolder<CraftingRecipe>> recipes) {

    public static TerminalCraftingRecipes resolve(ServerPlayer player, TerminalWorkspaceState work, boolean configured) {
        CraftingInput input = TerminalWorkstation.craftingInput(work, configured).input();
        var recipes = new ObjectArrayList<>(player.serverLevel().getRecipeManager().getRecipesFor(RecipeType.CRAFTING, input, player.level()));
        boolean limited = player.level().getGameRules().getBoolean(GameRules.RULE_LIMITED_CRAFTING);
        recipes.removeIf(recipe -> {
            if (limited && !recipe.value().isSpecial() && !player.getRecipeBook().contains(recipe)) return true;
            ItemStack output = recipe.value().assemble(input, player.registryAccess());
            return output.isEmpty() || !output.isItemEnabled(player.level().enabledFeatures());
        });
        recipes.sort(Comparator.comparing(recipe -> recipe.id().toString()));
        return new TerminalCraftingRecipes(input, new ObjectImmutableList<>(recipes));
    }

    public Optional<RecipeHolder<CraftingRecipe>> selected(TerminalWorkspaceState work) {
        for (var recipe : recipes)
            if (recipe.id().equals(work.context().recipeId())) return Optional.of(recipe);
        return recipes.isEmpty() ? Optional.empty() : Optional.of(recipes.getFirst());
    }

    public ItemStack output(ServerPlayer player, TerminalWorkspaceState work) {
        return selected(work).map(recipe -> recipe.value().assemble(input, player.registryAccess())).orElse(ItemStack.EMPTY);
    }

    /** Only IDs still matching the current server inputs can become the persistent selection. */
    public boolean select(TerminalWorkspaceState work, ResourceLocation id) {
        for (var recipe : recipes) {
            if (recipe.id().equals(id)) {
                work.context(new TerminalPatternContext(ResourceLocation.withDefaultNamespace("crafting"), id));
                return true;
            }
        }
        return false;
    }

    public void snapshot(ServerPlayer player, TerminalWorkspaceState work, CompoundTag tag) {
        ListTag entries = new ListTag();
        for (var recipe : recipes) {
            CompoundTag entry = new CompoundTag();
            entry.putString("id", recipe.id().toString());
            entry.put("output", recipe.value().assemble(input, player.registryAccess()).saveOptional(player.registryAccess()));
            entries.add(entry);
        }
        tag.put("crafting_recipes", entries);
        tag.putString("crafting_recipe", selected(work).map(recipe -> recipe.id().toString()).orElse(""));
        tag.put("result", output(player, work).saveOptional(player.registryAccess()));
    }
}
