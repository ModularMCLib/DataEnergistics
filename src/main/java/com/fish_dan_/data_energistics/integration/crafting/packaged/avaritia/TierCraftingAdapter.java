package com.fish_dan_.data_energistics.integration.crafting.packaged.avaritia;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.crafting.packaged.PackagedMachineAdapter;
import com.fish_dan_.data_energistics.api.crafting.packaged.PackagedMachineOperation;
import com.fish_dan_.data_energistics.common.crafting.packaged.recipe.PackagedCraftingGrid;
import com.fish_dan_.data_energistics.common.crafting.packaged.recipe.PackagedIngredientAssignment;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;

import committee.nova.mods.avaritia.api.common.crafting.ITierCraftingRecipe;
import committee.nova.mods.avaritia.api.common.crafting.TierInput;
import committee.nova.mods.avaritia.common.crafting.recipe.ShapedTableCraftingRecipe;
import committee.nova.mods.avaritia.common.tile.TierCraftTile;
import committee.nova.mods.avaritia.init.registry.ModRecipeTypes;
import committee.nova.mods.avaritia.init.registry.enums.ModCraftTier;
import committee.nova.mods.avaritia.api.common.wrapper.ItemStackWrapper;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.jspecify.annotations.Nullable;

import java.util.List;

/** Runs a recipe through Avaritia's native tier input and inventory. */
final class TierCraftingAdapter implements PackagedMachineAdapter {

    private static final ResourceLocation TYPE = ResourceLocation.fromNamespaceAndPath(
            "avaritia", "crafting_table_recipe");

    private final ModCraftTier tier;
    private final ResourceLocation id;

    TierCraftingAdapter(ModCraftTier tier) {
        this.tier = tier;
        this.id = Data_Energistics.id("avaritia_" + tier.name.toLowerCase(java.util.Locale.ROOT) + "_crafting");
    }

    @Override
    public ResourceLocation id() {
        return this.id;
    }

    @Override
    public ObjectSet<ResourceLocation> recipeTypes() {
        return ObjectSet.of(TYPE);
    }

    @Override
    public boolean recognizes(ServerLevel level, BlockPos position) {
        return table(level, position) != null;
    }

    @Override
    public @Nullable CompoundTag prepare(ServerLevel level, BlockPos position, Direction face,
                                         ResourceLocation recipeId, IPatternDetails pattern, KeyCounter[] inputs) {
        TierCraftTile table = table(level, position);
        if (table == null || !empty(table.getInventory())) return null;
        var holder = level.getRecipeManager().byKey(recipeId);
        if (holder.isEmpty() || !(holder.get().value() instanceof ITierCraftingRecipe recipe) ||
                recipe.getType() != ModRecipeTypes.CRAFTING_TABLE_RECIPE.get() ||
                recipe.getTier() > tier.ordinal() + 1) return null;

        int size = tier.size;
        ObjectList<ItemStack> grid;
        if (recipe instanceof ShapedTableCraftingRecipe shaped) {
            grid = PackagedCraftingGrid.assign(new ObjectArrayList<>(recipe.getIngredients()),
                    shaped.getWidth(), size, inputs);
        } else {
            var assigned = PackagedIngredientAssignment.match(new ObjectArrayList<>(recipe.getIngredients()), inputs);
            if (assigned == null || assigned.size() > size * size) return null;
            grid = new ObjectArrayList<>(size * size);
            for (int index = 0; index < size * size; index++) {
                grid.add(index < assigned.size() ? assigned.get(index) : ItemStack.EMPTY);
            }
        }
        if (grid == null || grid.size() != size * size) return null;
        TierInput nativeInput = TierInput.of(size, size, grid, tier.ordinal() + 1);
        if (!recipe.matches(nativeInput, level)) return null;
        var chosen = level.getRecipeManager().getRecipeFor(ModRecipeTypes.CRAFTING_TABLE_RECIPE.get(), nativeInput, level);
        if (chosen.isEmpty() || !chosen.get().id().equals(recipeId)) return null;
        ItemStack result = recipe.assemble(nativeInput, level.registryAccess());
        var remaining = recipe.getRemainingItems(nativeInput);
        var expected = new ObjectArrayList<ItemStack>();
        expected.add(result);
        expected.addAll(remaining);
        if (result.isEmpty() || !PackagedIngredientAssignment.outputsMatch(pattern, expected)) return null;

        CompoundTag progress = new CompoundTag();
        progress.put("inputs", saveStacks(grid, level.registryAccess()));
        progress.put("remaining", saveStacks(remaining, level.registryAccess()));
        progress.put("result", result.saveOptional(level.registryAccess()));
        return progress;
    }

    @Override
    public boolean advance(PackagedMachineOperation operation) {
        TierCraftTile table = table(operation.level(), operation.position());
        if (table == null) return false;
        CompoundTag progress = operation.progress();
        int slots = tier.size * tier.size;
        ObjectList<ItemStack> inputs = readStacks(operation, progress.getList("inputs", Tag.TAG_COMPOUND));
        ObjectList<ItemStack> remaining = readStacks(operation, progress.getList("remaining", Tag.TAG_COMPOUND));
        ItemStack result = ItemStack.parse(operation.level().registryAccess(), progress.getCompound("result"))
                .orElseThrow(() -> new IllegalArgumentException("Missing Avaritia table result"));
        if (inputs.size() != slots || remaining.size() != slots) {
            throw new IllegalArgumentException("Invalid persisted Avaritia table grid");
        }
        ItemStackWrapper inventory = table.getInventory();
        if (!progress.getBoolean("delivered")) {
            for (int index = 0; index < slots; index++) {
                if (!inventory.getStackInSlot(index).isEmpty()) return false;
            }
            for (int index = 0; index < slots; index++) {
                ItemStack input = inputs.get(index);
                if (input.isEmpty()) continue;
                ItemStack rejected = inventory.insertItem(index, input.copy(), false);
                if (!rejected.isEmpty()) throw new IllegalStateException("Avaritia table input insertion changed");
                operation.delivered(AEItemKey.of(input), input.getCount());
            }
            progress.putBoolean("delivered", true);
            operation.changed();
            return true;
        }
        for (int index = 0; index < slots; index++) {
            if (!ItemStack.matches(inventory.getStackInSlot(index), inputs.get(index))) {
                throw new IllegalStateException("Avaritia table input changed outside this operation");
            }
        }
        // The native tier table is an instant crafting menu. Apply its exact remainder list after validating it.
        for (int index = 0; index < slots; index++) {
            inventory.setStackInSlot(index, ItemStack.EMPTY);
            ItemStack returned = remaining.get(index).copy();
            if (!returned.isEmpty()) {
                ItemStack rejected = inventory.insertItem(index, returned.copy(), false);
                if (!rejected.isEmpty()) throw new IllegalStateException("Avaritia table remainder cannot be retained");
                operation.returned(AEItemKey.of(returned), returned.getCount());
            }
        }
        operation.returned(AEItemKey.of(result), result.getCount());
        operation.complete();
        return true;
    }

    private @Nullable TierCraftTile table(ServerLevel level, BlockPos position) {
        if (!level.isLoaded(position) || !(level.getBlockEntity(position) instanceof TierCraftTile table)) return null;
        return table.tier == this.tier ? table : null;
    }

    private static boolean empty(ItemStackWrapper inventory) {
        for (int index = 0; index < inventory.getSlots(); index++) {
            if (!inventory.getStackInSlot(index).isEmpty()) return false;
        }
        return true;
    }

    private static ListTag saveStacks(List<? extends ItemStack> stacks, HolderLookup.Provider registries) {
        ListTag encoded = new ListTag();
        for (ItemStack stack : stacks) encoded.add(stack.saveOptional(registries));
        return encoded;
    }

    private static ObjectList<ItemStack> readStacks(PackagedMachineOperation operation, ListTag encoded) {
        ObjectList<ItemStack> stacks = new ObjectArrayList<>(encoded.size());
        for (int index = 0; index < encoded.size(); index++) {
            stacks.add(ItemStack.parseOptional(operation.level().registryAccess(), encoded.getCompound(index)));
        }
        return stacks;
    }
}
