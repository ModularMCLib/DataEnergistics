package com.fish_dan_.data_energistics.common.terminal.player.encoding;

import com.fish_dan_.data_energistics.common.terminal.player.persistence.TerminalPlayerState;
import com.fish_dan_.data_energistics.common.terminal.player.storage.TerminalGhostInventory;
import com.fish_dan_.data_energistics.common.terminal.player.workstation.TerminalWorkspace;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jspecify.annotations.NullMarked;

import java.util.Comparator;

/** Validates a complete viewer transfer before replacing any of the active page's ghost configuration. */
@NullMarked
public final class TerminalRecipeTransfer {

    private TerminalRecipeTransfer() {}

    public static void apply(ServerPlayer player, TerminalPlayerState state, CompoundTag data) {
        TerminalWorkspace page = TerminalWorkspace.byId(data.getString("workspace"));
        if (!state.work().encoding() || page != state.workspace() || !page.supportsEncoding())
            throw new IllegalArgumentException("Recipe transfer no longer targets the active encoder");
        TerminalGhostInventory inputs = new TerminalGhostInventory(page.patternInputSlots(), () -> {});
        TerminalGhostInventory outputs = new TerminalGhostInventory(page.patternOutputSlots(), () -> {});
        inputs.load(data.getList("inputs", Tag.TAG_COMPOUND), player.registryAccess());
        outputs.load(data.getList("outputs", Tag.TAG_COMPOUND), player.registryAccess());
        TerminalPatternContext context = TerminalPatternContext.load(data.getCompound("context"));
        int selectedRecipe = 0;
        if (page != TerminalWorkspace.PROCESSING) {
            for (int index = 0; index < inputs.size(); index++) {
                GenericStack stack = inputs.get(index);
                if (stack != null) {
                    if (!(stack.what() instanceof AEItemKey)) throw new IllegalArgumentException("Vanilla encoders require item ingredients");
                    inputs.set(index, new GenericStack(stack.what(), 1));
                }
            }
        }
        if (page == TerminalWorkspace.STONECUTTING && context.recipeId() != null) {
            GenericStack input = inputs.get(0);
            if (input == null) throw new IllegalArgumentException("Stonecutting transfer has no input");
            var recipes = new ObjectArrayList<>(player.serverLevel().getRecipeManager().getRecipesFor(RecipeType.STONECUTTING,
                    new SingleRecipeInput(((AEItemKey) input.what()).toStack(1)), player.level()));
            recipes.sort(Comparator.comparing(recipe -> recipe.id().toString()));
            selectedRecipe = -1;
            for (int index = 0; index < recipes.size(); index++) if (recipes.get(index).id().equals(context.recipeId())) selectedRecipe = index;
            if (selectedRecipe < 0) throw new IllegalArgumentException("Transferred stonecutting recipe does not match its input");
        }
        state.work().patternInputs.copyFrom(inputs);
        state.work().patternOutputs.copyFrom(outputs);
        state.work().context(context);
        state.work().recipeIndex(selectedRecipe);
    }
}
