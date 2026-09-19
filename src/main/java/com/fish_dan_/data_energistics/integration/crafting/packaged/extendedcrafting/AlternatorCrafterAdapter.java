package com.fish_dan_.data_energistics.integration.crafting.packaged.extendedcrafting;

import com.fish_dan_.data_energistics.api.crafting.packaged.PackagedMachineAdapter;
import com.fish_dan_.data_energistics.api.crafting.packaged.PackagedMachineOperation;
import com.fish_dan_.data_energistics.common.crafting.packaged.recipe.PackagedCraftingGrid;
import com.fish_dan_.data_energistics.common.crafting.packaged.recipe.PackagedIngredientAssignment;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;

import com.blakebr0.cucumber.inventory.BaseItemStackHandler;
import com.blakebr0.extendedcrafting.api.crafting.IEnderCrafterRecipe;
import com.blakebr0.extendedcrafting.api.crafting.IFluxCrafterRecipe;
import com.blakebr0.extendedcrafting.crafting.recipe.ShapedEnderCrafterRecipe;
import com.blakebr0.extendedcrafting.crafting.recipe.ShapedFluxCrafterRecipe;
import com.blakebr0.extendedcrafting.init.ModRecipeTypes;
import com.blakebr0.extendedcrafting.tileentity.EnderCrafterTileEntity;
import com.blakebr0.extendedcrafting.tileentity.FluxCrafterTileEntity;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.jspecify.annotations.Nullable;

/** Inserts the real 3x3 grid and waits for the machine's alternators, energy and timer to produce slot 9. */
final class AlternatorCrafterAdapter implements PackagedMachineAdapter {

    private final boolean flux;
    private final ResourceLocation type;

    AlternatorCrafterAdapter(boolean flux) {
        this.flux = flux;
        this.type = ResourceLocation.fromNamespaceAndPath("extendedcrafting", flux ? "flux_crafter" : "ender_crafter");
    }

    @Override
    public ResourceLocation id() {
        return this.type;
    }

    @Override
    public ObjectSet<ResourceLocation> recipeTypes() {
        return ObjectSet.of(this.type);
    }

    @Override
    public boolean recognizes(ServerLevel level, BlockPos position) {
        return inventory(level, position) != null;
    }

    private @Nullable BaseItemStackHandler inventory(ServerLevel level, BlockPos position) {
        if (!level.isLoaded(position)) return null;
        var tile = level.getBlockEntity(position);
        if (this.flux && tile instanceof FluxCrafterTileEntity crafter) return crafter.getInventory();
        if (!this.flux && tile instanceof EnderCrafterTileEntity crafter) return crafter.getInventory();
        return null;
    }

    private @Nullable Recipe<CraftingInput> recipe(RecipeHolder<?> holder) {
        if (this.flux && holder.value() instanceof IFluxCrafterRecipe recipe) return recipe;
        if (!this.flux && holder.value() instanceof IEnderCrafterRecipe recipe) return recipe;
        return null;
    }

    @Override
    public @Nullable CompoundTag prepare(ServerLevel level, BlockPos position, Direction face,
                                         ResourceLocation recipeId, IPatternDetails pattern, KeyCounter[] inputs) {
        var inventory = inventory(level, position);
        if (inventory == null || !empty(inventory)) return null;
        var holder = level.getRecipeManager().byKey(recipeId);
        var recipe = holder.isEmpty() ? null : recipe(holder.get());
        if (recipe == null) return null;
        int width = recipe instanceof ShapedEnderCrafterRecipe shaped ? shaped.getWidth() :
                recipe instanceof ShapedFluxCrafterRecipe shaped ? shaped.getWidth() : 3;
        var grid = PackagedCraftingGrid.assign(new ObjectArrayList<>(recipe.getIngredients()), width, 3, inputs);
        if (grid == null) return null;
        var input = CraftingInput.of(3, 3, grid);
        if (!recipe.matches(input, level)) return null;
        var chosen = this.flux ?
                level.getRecipeManager().getRecipeFor(ModRecipeTypes.FLUX_CRAFTER.get(), input, level) :
                level.getRecipeManager().getRecipeFor(ModRecipeTypes.ENDER_CRAFTER.get(), input, level);
        if (chosen.isEmpty() || chosen.get().value() != recipe) return null;
        var result = recipe.assemble(input, level.registryAccess());
        // These two real machines shrink inputs directly; they do not apply Recipe#getRemainingItems.
        if (result.isEmpty() || !PackagedIngredientAssignment.outputsMatch(pattern, ObjectArrayList.of(result))) return null;
        var progress = new CompoundTag();
        var slots = new ListTag();
        for (int slot = 0; slot < 9; slot++) {
            var stack = grid.get(slot);
            if (!stack.isEmpty() && !inventory.insertItem(slot, stack, true).isEmpty()) return null;
            slots.add(stack.saveOptional(level.registryAccess()));
        }
        progress.put("inputs", slots);
        progress.put("result", result.save(level.registryAccess()));
        return progress;
    }

    @Override
    public boolean advance(PackagedMachineOperation operation) {
        var inventory = inventory(operation.level(), operation.position());
        if (inventory == null) return false;
        var progress = operation.progress();
        var result = ItemStack.parse(operation.level().registryAccess(), progress.getCompound("result")).orElseThrow();
        if (progress.getBoolean("delivered")) {
            var actual = inventory.getStackInSlot(9);
            if (actual.isEmpty()) return false;
            if (!ItemStack.matches(actual, result)) throw new IllegalStateException("Unexpected alternator crafter output");
            for (int slot = 0; slot < 9; slot++) if (!inventory.getStackInSlot(slot).isEmpty()) return false;
            var extracted = inventory.extractItem(9, actual.getCount(), false);
            if (extracted.isEmpty()) return false;
            operation.returned(AEItemKey.of(extracted), extracted.getCount());
            operation.complete();
            return true;
        }
        if (!empty(inventory)) return false;
        var slots = progress.getList("inputs", Tag.TAG_COMPOUND);
        if (slots.size() != 9) throw new IllegalArgumentException("Invalid persisted crafting grid");
        var grid = new ObjectArrayList<ItemStack>();
        for (int slot = 0; slot < 9; slot++) {
            var stack = ItemStack.parseOptional(operation.level().registryAccess(), slots.getCompound(slot));
            if (!stack.isEmpty() && !inventory.insertItem(slot, stack, true).isEmpty()) return false;
            grid.add(stack);
        }
        var holder = operation.level().getRecipeManager().byKey(operation.recipeId());
        var recipe = holder.isEmpty() ? null : recipe(holder.get());
        var input = CraftingInput.of(3, 3, grid);
        if (recipe == null || !recipe.matches(input, operation.level()) ||
                !ItemStack.matches(result, recipe.assemble(input, operation.level().registryAccess()))) {
            throw new IllegalStateException("Alternator crafter recipe changed after admission");
        }
        for (int slot = 0; slot < 9; slot++) {
            var stack = grid.get(slot);
            if (stack.isEmpty()) continue;
            var remainder = inventory.insertItem(slot, stack.copy(), false);
            int accepted = stack.getCount() - remainder.getCount();
            if (accepted > 0) operation.delivered(AEItemKey.of(stack), accepted);
            if (!remainder.isEmpty()) throw new IllegalStateException("Alternator input capacity changed after simulation");
        }
        progress.putBoolean("delivered", true);
        operation.changed();
        return true;
    }

    private static boolean empty(BaseItemStackHandler inventory) {
        for (int slot = 0; slot < inventory.getSlots(); slot++) if (!inventory.getStackInSlot(slot).isEmpty()) return false;
        return true;
    }
}
