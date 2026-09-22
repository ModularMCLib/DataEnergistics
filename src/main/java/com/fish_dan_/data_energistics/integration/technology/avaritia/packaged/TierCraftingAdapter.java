package com.fish_dan_.data_energistics.integration.technology.avaritia.packaged;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.crafting.packaged.PackagedMachineAdapter;
import com.fish_dan_.data_energistics.api.crafting.packaged.PackagedMachineOperation;
import com.fish_dan_.data_energistics.common.crafting.packaged.recipe.PackagedCraftingGrid;
import com.fish_dan_.data_energistics.common.crafting.packaged.recipe.PackagedIngredientAssignment;
import com.fish_dan_.data_energistics.common.crafting.packaged.recipe.PackagedOutputMatching;

import appeng.api.crafting.IPatternDetails;
import appeng.api.ids.AEComponents;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.pattern.AEProcessingPattern;
import appeng.crafting.pattern.EncodedProcessingPattern;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

import com.mojang.authlib.GameProfile;
import committee.nova.mods.avaritia.api.common.crafting.ITierCraftingRecipe;
import committee.nova.mods.avaritia.api.common.crafting.TierInput;
import committee.nova.mods.avaritia.api.common.wrapper.ItemStackWrapper;
import committee.nova.mods.avaritia.common.crafting.recipe.ShapedTableCraftingRecipe;
import committee.nova.mods.avaritia.common.tile.TierCraftTile;
import committee.nova.mods.avaritia.init.registry.ModRecipeTypes;
import committee.nova.mods.avaritia.init.registry.enums.ModCraftTier;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.jspecify.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Runs a recipe through Avaritia's native tier input and inventory. */
public final class TierCraftingAdapter implements PackagedMachineAdapter {

    private static final ResourceLocation TYPE = ResourceLocation.fromNamespaceAndPath(
            "avaritia", "crafting_table_recipe");

    private final ModCraftTier tier;
    private final ResourceLocation id;

    public TierCraftingAdapter(ModCraftTier tier) {
        this.tier = tier;
        this.id = Data_Energistics.id("avaritia_" + tier.name().toLowerCase(Locale.ROOT) + "_crafting");
    }

    @Override
    public ResourceLocation id() {
        return this.id;
    }

    @Override
    public ObjectSet<ResourceLocation> recipeTypes() {
        return ObjectSet.of(TYPE, ResourceLocation.fromNamespaceAndPath(
                "avaritia", tier.name().toLowerCase(Locale.ROOT) + "_craft"),
                ResourceLocation.fromNamespaceAndPath("avaritia", tier.name));
    }

    @Override
    public boolean recognizes(ServerLevel level, BlockPos position) {
        return table(level, position) != null;
    }

    @Override
    public @Nullable ItemStack completeEncoding(ServerLevel level, ResourceLocation recipeId, ItemStack encodedPattern) {
        var processing = encodedPattern.get(AEComponents.ENCODED_PROCESSING_PATTERN);
        var holder = level.getRecipeManager().byKey(recipeId);
        if (processing == null || holder.isEmpty() || !(holder.get().value() instanceof ITierCraftingRecipe recipe)) return null;
        var supplied = new KeyCounter();
        for (var input : processing.sparseInputs()) if (input != null) supplied.add(input.what(), input.amount());
        int recipeTier = recipe.getTier();
        if (recipeTier < 1 || recipeTier > 4) return null;
        int size = recipeTier * 2 + 1;
        int width = recipe instanceof ShapedTableCraftingRecipe shaped ? shaped.getWidth() : size;
        var grid = PackagedCraftingGrid.assign(new ObjectArrayList<>(recipe.getIngredients()), width, size, new KeyCounter[] { supplied });
        if (grid == null) return null;
        var nativeInput = TierInput.of(size, size, grid, recipeTier);
        if (!recipe.matches(nativeInput, level)) return null;
        var selected = level.getRecipeManager().getRecipeFor(ModRecipeTypes.CRAFTING_TABLE_RECIPE.get(), nativeInput, level);
        if (selected.isEmpty() || !selected.get().id().equals(recipeId)) return null;
        ItemStack produced = recipe.assemble(nativeInput, level.registryAccess());
        var returned = returnedStacks(grid, consumedSlots(nativeInput, size), recipe.getRemainingItems(nativeInput));
        var pattern = new AEProcessingPattern(AEItemKey.of(encodedPattern));
        if (!PackagedOutputMatching.matchesWithAdditionalReturns(pattern, produced, returned)) return null;
        var missing = new KeyCounter();
        for (ItemStack stack : returned) missing.add(AEItemKey.of(stack), stack.getCount());
        var additions = new KeyCounter();
        var returnKeys = new ObjectArrayList<AEItemKey>();
        for (var entry : missing) returnKeys.add((AEItemKey) entry.getKey());
        for (var key : returnKeys) {
            long original = missing.get(key);
            long low = 0;
            long high = original;
            while (low < high) {
                long removed = low + (high - low + 1) / 2;
                var remaining = new ObjectArrayList<ItemStack>();
                for (var resource : missing) {
                    long count = resource.getLongValue() - (resource.getKey().equals(key) ? removed : 0);
                    if (count > 0) remaining.add(((AEItemKey) resource.getKey()).toStack(Math.toIntExact(count)));
                }
                if (PackagedOutputMatching.matchesWithAdditionalReturns(pattern, produced, remaining)) low = removed;
                else high = removed - 1;
            }
            if (low > 0) {
                additions.add(key, low);
                missing.set(key, original - low);
            }
        }
        var outputs = new ObjectArrayList<@Nullable GenericStack>(processing.sparseOutputs());
        int tail = outputs.size();
        while (tail > 0 && outputs.get(tail - 1) == null) tail--;
        for (var entry : additions) {
            if (tail >= AEProcessingPattern.MAX_OUTPUT_SLOTS) return null;
            var output = new GenericStack(entry.getKey(), entry.getLongValue());
            if (tail < outputs.size()) outputs.set(tail, output);
            else outputs.add(output);
            tail++;
        }
        ItemStack copy = encodedPattern.copy();
        copy.set(AEComponents.ENCODED_PROCESSING_PATTERN, new EncodedProcessingPattern(processing.sparseInputs(), outputs));
        return copy;
    }

    @Override
    public @Nullable CompoundTag prepare(ServerLevel level, BlockPos position, Direction face,
                                         ResourceLocation recipeId, IPatternDetails pattern, KeyCounter[] inputs) {
        TierCraftTile table = table(level, position);
        if (table == null || !empty(table.getInventory())) return null;
        // Avaritia's menu exposes one result stack per native operation. It does
        // not have a bulk input/output transaction, so a packaged operation must
        // never emulate batching by taking the result repeatedly.
        var holder = level.getRecipeManager().byKey(recipeId);
        if (holder.isEmpty() || !(holder.get().value() instanceof ITierCraftingRecipe recipe) ||
                recipe.getType() != ModRecipeTypes.CRAFTING_TABLE_RECIPE.get() ||
                recipe.getTier() > tier.ordinal() + 1)
            return null;

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
        var nativeRemaining = level.getRecipeManager().getRemainingItemsFor(
                ModRecipeTypes.CRAFTING_TABLE_RECIPE.get(), nativeInput, level);
        var consumed = consumedSlots(nativeInput, size);
        var returned = returnedStacks(grid, consumed, nativeRemaining);
        if (!PackagedOutputMatching.matchesWithAdditionalReturns(pattern, result, returned)) return null;

        CompoundTag progress = new CompoundTag();
        progress.put("inputs", saveStacks(grid, level.registryAccess()));
        progress.put("returns", saveStacks(returned, level.registryAccess()));
        progress.put("result", result.saveOptional(level.registryAccess()));
        return progress;
    }

    @Override
    public long batchCapacity(ServerLevel level, BlockPos position, Direction face, ResourceLocation recipeId,
                              IPatternDetails pattern, KeyCounter[] prototype, long requestedCount) {
        TierCraftTile table = table(level, position);
        if (table == null || !empty(table.getInventory()) || requestedCount <= 0) return 0;
        var holder = level.getRecipeManager().byKey(recipeId);
        if (holder.isEmpty() || !(holder.get().value() instanceof ITierCraftingRecipe recipe) ||
                recipe.getType() != ModRecipeTypes.CRAFTING_TABLE_RECIPE.get() || recipe.getTier() > tier.ordinal() + 1)
            return 0;
        int size = tier.size;
        ObjectList<ItemStack> grid;
        if (recipe instanceof ShapedTableCraftingRecipe shaped) {
            grid = PackagedCraftingGrid.assign(new ObjectArrayList<>(recipe.getIngredients()), shaped.getWidth(), size, prototype);
        } else {
            var assigned = PackagedIngredientAssignment.match(new ObjectArrayList<>(recipe.getIngredients()), prototype);
            if (assigned == null || assigned.size() > size * size) return 0;
            grid = new ObjectArrayList<>(size * size);
            for (int index = 0; index < size * size; index++) grid.add(index < assigned.size() ? assigned.get(index) : ItemStack.EMPTY);
        }
        if (grid == null || grid.size() != size * size) return 0;
        TierInput nativeInput = TierInput.of(size, size, grid, tier.ordinal() + 1);
        if (!recipe.matches(nativeInput, level)) return 0;
        var chosen = level.getRecipeManager().getRecipeFor(ModRecipeTypes.CRAFTING_TABLE_RECIPE.get(), nativeInput, level);
        if (chosen.isEmpty() || !chosen.get().id().equals(recipeId)) return 0;
        ItemStack result = recipe.assemble(nativeInput, level.registryAccess());
        if (result.isEmpty()) return 0;
        // A remainder is valid for a single native menu transaction, but this
        // table cannot atomically process more than one transaction.
        return 1;
    }

    @Override
    public boolean advance(PackagedMachineOperation operation) {
        TierCraftTile table = table(operation.level(), operation.position());
        if (table == null) return false;
        CompoundTag progress = operation.progress();
        int slots = tier.size * tier.size;
        ObjectList<ItemStack> inputs = readStacks(operation, progress.getList("inputs", Tag.TAG_COMPOUND));
        ObjectList<ItemStack> returns = readStacks(operation, progress.getList("returns", Tag.TAG_COMPOUND));
        ItemStack result = ItemStack.parse(operation.level().registryAccess(), progress.getCompound("result"))
                .orElseThrow(() -> new IllegalArgumentException("Missing Avaritia table result"));
        if (inputs.size() != slots) {
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
            ItemStack actualInput = inventory.getStackInSlot(index);
            if (!actualInput.isEmpty() && (!ItemStack.isSameItemSameComponents(actualInput, inputs.get(index)) ||
                    actualInput.getCount() > inputs.get(index).getCount())) {
                throw new IllegalStateException("Avaritia table input changed outside this operation");
            }
        }
        var fake = FakePlayerFactory.get(operation.level(), new GameProfile(
                UUID.nameUUIDFromBytes(operation.id().toString().getBytes(StandardCharsets.UTF_8)),
                "data_energistics_packaged"));
        var menu = table.createMenu(0, fake.getInventory());
        var output = menu.getSlot(0);
        ItemStack actual = output.getItem().copy();
        if (actual.isEmpty()) return false;
        if (!PackagedOutputMatching.matches(operation, result.copyWithCount(actual.getCount()), actual))
            throw new IllegalStateException("Unexpected Avaritia table output");
        ItemStack taken = output.remove(actual.getCount());
        if (!ItemStack.matches(actual, taken)) throw new IllegalStateException("Avaritia result extraction changed");
        output.onTake(fake, taken);
        actual = taken;
        operation.returned(AEItemKey.of(actual), actual.getCount());

        var actualReturns = new ObjectArrayList<ItemStack>();
        for (int index = 0; index < slots; index++) {
            ItemStack returned = inventory.getStackInSlot(index).copy();
            if (!returned.isEmpty()) actualReturns.add(returned);
        }
        for (int index = 0; index < fake.getInventory().getContainerSize(); index++) {
            ItemStack returned = fake.getInventory().getItem(index).copy();
            if (!returned.isEmpty()) actualReturns.add(returned);
        }
        if (!sameStacks(actualReturns, returns)) {
            throw new IllegalStateException("Avaritia table native remainder changed");
        }
        for (int index = 0; index < slots; index++) inventory.setStackInSlot(index, ItemStack.EMPTY);
        fake.getInventory().clearContent();
        for (ItemStack returned : actualReturns) operation.returned(AEItemKey.of(returned), returned.getCount());
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

    private static int[] consumedSlots(TierInput input, int tableSize) {
        var consumed = new ObjectArrayList<Integer>();
        for (int row = 0; row < input.height(); row++) {
            for (int column = 0; column < input.width(); column++) {
                if (!input.getItem(column, row).isEmpty()) {
                    consumed.add((input.top() + row) * tableSize + input.left() + column);
                }
            }
        }
        return consumed.stream().mapToInt(Integer::intValue).toArray();
    }

    private static ObjectList<ItemStack> returnedStacks(ObjectList<ItemStack> grid, int[] consumed,
                                                        List<? extends ItemStack> nativeRemaining) {
        var consumedSet = new IntOpenHashSet();
        for (int index : consumed) consumedSet.add(index);
        var returned = new ObjectArrayList<ItemStack>();
        for (int index = 0; index < grid.size(); index++) {
            ItemStack leftover = grid.get(index).copy();
            if (consumedSet.contains(index)) leftover.shrink(1);
            if (!leftover.isEmpty()) returned.add(leftover);
        }
        for (ItemStack remainder : nativeRemaining) if (!remainder.isEmpty()) returned.add(remainder.copy());
        return returned;
    }

    private static boolean sameStacks(List<? extends ItemStack> actual, List<? extends ItemStack> expected) {
        var actualCounts = new Object2LongLinkedOpenHashMap<AEItemKey>();
        var expectedCounts = new Object2LongLinkedOpenHashMap<AEItemKey>();
        for (ItemStack stack : actual) actualCounts.addTo(AEItemKey.of(stack), stack.getCount());
        for (ItemStack stack : expected) expectedCounts.addTo(AEItemKey.of(stack), stack.getCount());
        return actualCounts.equals(expectedCounts);
    }
}
