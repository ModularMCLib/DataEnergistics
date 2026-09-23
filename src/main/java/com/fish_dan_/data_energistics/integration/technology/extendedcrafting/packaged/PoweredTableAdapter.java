package com.fish_dan_.data_energistics.integration.technology.extendedcrafting.packaged;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.crafting.packaged.PackagedMachineAdapter;
import com.fish_dan_.data_energistics.api.crafting.packaged.PackagedMachineOperation;
import com.fish_dan_.data_energistics.common.crafting.packaged.recipe.PackagedCraftingGrid;
import com.fish_dan_.data_energistics.common.crafting.packaged.recipe.PackagedIngredientAssignment;
import com.fish_dan_.data_energistics.common.crafting.packaged.recipe.PackagedOutputMatching;

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

import com.blakebr0.extendedcrafting.api.TableCraftingInput;
import com.blakebr0.extendedcrafting.api.crafting.ITableRecipe;
import com.blakebr0.extendedcrafting.crafting.recipe.ShapedTableRecipe;
import com.blakebr0.extendedcrafting.init.ModRecipeTypes;
import com.blakebr0.extendedcrafting.tileentity.AutoTableTileEntity;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Uses the powered tables' physical grid and output; their own tick pays power and advances time.
 */
@NullMarked
public final class PoweredTableAdapter implements PackagedMachineAdapter {

    @Override
    public ResourceLocation id() {
        return Data_Energistics.id("extended_crafting_powered_table");
    }

    @Override
    public ObjectSet<ResourceLocation> recipeTypes() {
        return ObjectSet.of(ResourceLocation.fromNamespaceAndPath("extendedcrafting", "table"),
                ResourceLocation.fromNamespaceAndPath("extendedcrafting", "basic_crafting"),
                ResourceLocation.fromNamespaceAndPath("extendedcrafting", "advanced_crafting"),
                ResourceLocation.fromNamespaceAndPath("extendedcrafting", "elite_crafting"),
                ResourceLocation.fromNamespaceAndPath("extendedcrafting", "ultimate_crafting"));
    }

    @Override
    public ObjectSet<ResourceLocation> workstationItemIds() {
        return ObjectSet.of(
                ResourceLocation.fromNamespaceAndPath("extendedcrafting", "basic_auto_table"),
                ResourceLocation.fromNamespaceAndPath("extendedcrafting", "advanced_auto_table"),
                ResourceLocation.fromNamespaceAndPath("extendedcrafting", "elite_auto_table"),
                ResourceLocation.fromNamespaceAndPath("extendedcrafting", "ultimate_auto_table"));
    }

    @Override
    public boolean recognizes(ServerLevel level, BlockPos position) {
        return machine(level, position) != null;
    }

    @Override
    public @Nullable CompoundTag prepare(ServerLevel level, BlockPos position, Direction face,
                                         ResourceLocation recipeId, IPatternDetails pattern, KeyCounter[] inputs) {
        var tile = machine(level, position);
        var holder = level.getRecipeManager().byKey(recipeId);
        if (tile == null || !empty(tile) || holder.isEmpty() || !(holder.get().value() instanceof ITableRecipe recipe))
            return null;
        int size = tile.getTier() * 2 + 1;
        int width = recipe instanceof ShapedTableRecipe shaped ? shaped.getWidth() : size;
        var grid = PackagedCraftingGrid.assign(new ObjectArrayList<>(recipe.getIngredients()), width, size, inputs);
        if (grid == null) return null;
        var nativeInput = TableCraftingInput.of(size, size, grid, tile.getTier());
        var selected = level.getRecipeManager().getRecipeFor(ModRecipeTypes.TABLE.get(), nativeInput, level);
        if (!recipe.matches(nativeInput, level) || selected.isEmpty() || !selected.get().id().equals(recipeId))
            return null;
        ItemStack result = recipe.assemble(nativeInput, level.registryAccess());
        var nativeRemaining = recipe.getRemainingItems(nativeInput);
        if (result.isEmpty() || nativeRemaining.size() != nativeInput.size()) return null;
        var remaining = new ObjectArrayList<ItemStack>();
        for (int i = 0; i < size * size; i++) remaining.add(ItemStack.EMPTY);
        for (int y = 0; y < nativeInput.height(); y++)
            for (int x = 0; x < nativeInput.width(); x++)
                remaining.set((y + nativeInput.top()) * size + x + nativeInput.left(), nativeRemaining.get(y * nativeInput.width() + x));
        var expected = new ObjectArrayList<ItemStack>();
        expected.add(result);
        expected.addAll(remaining);
        if (!PackagedIngredientAssignment.outputsMatch(pattern, expected)) return null;
        var encodedInputs = new ListTag();
        var encodedRemaining = new ListTag();
        for (ItemStack stack : grid) encodedInputs.add(stack.saveOptional(level.registryAccess()));
        for (ItemStack stack : remaining) encodedRemaining.add(stack.saveOptional(level.registryAccess()));
        var progress = new CompoundTag();
        progress.putInt("size", size);
        progress.put("inputs", encodedInputs);
        progress.put("remaining", encodedRemaining);
        progress.put("result", result.save(level.registryAccess()));
        return progress;
    }

    @Override
    public boolean advance(PackagedMachineOperation operation) {
        var tile = machine(operation.level(), operation.position());
        if (tile == null) return false;
        var progress = operation.progress();
        int size = progress.getInt("size");
        if (size != tile.getTier() * 2 + 1) throw new IllegalStateException("Powered table tier changed");
        var inventory = tile.getInventory();
        int outputSlot = size * size;
        var encoded = progress.getList("inputs", Tag.TAG_COMPOUND);
        var remaining = progress.getList("remaining", Tag.TAG_COMPOUND);
        if (encoded.size() != outputSlot || remaining.size() != outputSlot)
            throw new IllegalArgumentException("Invalid powered table grid");
        if (!progress.getBoolean("delivered")) {
            if (!empty(tile)) return false;
            for (int slot = 0; slot < outputSlot; slot++) {
                ItemStack stack = ItemStack.parseOptional(operation.level().registryAccess(), encoded.getCompound(slot));
                if (stack.isEmpty()) continue;
                inventory.setStackInSlot(slot, stack.copy());
                operation.delivered(AEItemKey.of(stack), stack.getCount());
            }
            progress.putBoolean("delivered", true);
            operation.changed();
            return true;
        }
        ItemStack actual = inventory.getStackInSlot(outputSlot).copy();
        if (actual.isEmpty()) return false;
        ItemStack expected = ItemStack.parse(operation.level().registryAccess(), progress.getCompound("result")).orElseThrow();
        if (!PackagedOutputMatching.matches(operation, expected, actual))
            throw new IllegalStateException("Unexpected powered table result");
        for (int slot = 0; slot < outputSlot; slot++) {
            ItemStack expectedRemaining = ItemStack.parseOptional(operation.level().registryAccess(), remaining.getCompound(slot));
            if (!ItemStack.matches(expectedRemaining, inventory.getStackInSlot(slot)))
                throw new IllegalStateException("Powered table remainder changed");
        }
        inventory.setStackInSlot(outputSlot, ItemStack.EMPTY);
        operation.returned(AEItemKey.of(actual), actual.getCount());
        for (int slot = 0; slot < outputSlot; slot++) {
            ItemStack returned = inventory.getStackInSlot(slot).copy();
            if (returned.isEmpty()) continue;
            inventory.setStackInSlot(slot, ItemStack.EMPTY);
            operation.returned(AEItemKey.of(returned), returned.getCount());
        }
        operation.complete();
        return true;
    }

    private static boolean empty(AutoTableTileEntity tile) {
        for (int slot = 0; slot < tile.getInventory().getSlots(); slot++)
            if (!tile.getInventory().getStackInSlot(slot).isEmpty()) return false;
        return true;
    }

    private static @Nullable AutoTableTileEntity machine(ServerLevel level, BlockPos position) {
        return level.isLoaded(position) && level.getBlockEntity(position) instanceof AutoTableTileEntity tile ? tile : null;
    }
}
