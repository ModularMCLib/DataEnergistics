package com.fish_dan_.data_energistics.integration.technology.extendedcrafting.packaged;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.crafting.packaged.PackagedMachineAdapter;
import com.fish_dan_.data_energistics.api.crafting.packaged.PackagedMachineOperation;
import com.fish_dan_.data_energistics.common.crafting.packaged.recipe.PackagedIngredientAssignment;
import com.fish_dan_.data_energistics.common.crafting.packaged.recipe.PackagedOutputMatching;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;

import com.blakebr0.cucumber.inventory.BaseItemStackHandler;
import com.blakebr0.extendedcrafting.api.crafting.ICombinationRecipe;
import com.blakebr0.extendedcrafting.init.ModRecipeTypes;
import com.blakebr0.extendedcrafting.tileentity.CraftingCoreTileEntity;
import com.blakebr0.extendedcrafting.tileentity.PedestalTileEntity;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.jspecify.annotations.Nullable;

import java.math.BigInteger;
import java.util.List;

/** Drives Extended Crafting's real combination core and its shared pedestal inventory. */
public final class CombinationCraftingAdapter implements PackagedMachineAdapter {

    private static final ResourceLocation TYPE = ResourceLocation.fromNamespaceAndPath(
            "extendedcrafting", "combination");

    @Override
    public ResourceLocation id() {
        return Data_Energistics.id("extended_crafting_combination");
    }

    @Override
    public ObjectSet<ResourceLocation> recipeTypes() {
        return ObjectSet.of(TYPE);
    }

    @Override
    public boolean recognizes(ServerLevel level, BlockPos position) {
        return layout(level, position) != null;
    }

    @Override
    public @Nullable CompoundTag prepare(ServerLevel level, BlockPos position, Direction face,
                                         ResourceLocation recipeId, IPatternDetails pattern, KeyCounter[] inputs) {
        Layout layout = layout(level, position);
        if (layout == null || !empty(level, layout.core(), layout.pedestals())) return null;
        var holder = level.getRecipeManager().byKey(recipeId);
        if (holder.isEmpty() || !(holder.get().value() instanceof ICombinationRecipe recipe) ||
                recipe.getType() != ModRecipeTypes.COMBINATION.get())
            return null;

        var ingredients = ingredients(recipe);
        ObjectList<ItemStack> assigned = PackagedIngredientAssignment.match(ingredients, inputs);
        if (assigned == null || assigned.size() != ingredients.size() ||
                assigned.size() - 1 > layout.pedestals().size())
            return null;

        ObjectList<BlockPos> selected = new ObjectArrayList<>(assigned.size() - 1);
        for (BlockPos pedestal : layout.pedestals()) {
            if (selected.size() == assigned.size() - 1) break;
            selected.add(pedestal);
        }
        if (selected.size() != assigned.size() - 1) return null;
        ObjectList<ItemStack> machineInput = new ObjectArrayList<>(assigned);
        CraftingInput input = craftingInput(machineInput);
        if (!recipe.matches(input, level)) return null;
        var chosen = level.getRecipeManager().getRecipeFor(ModRecipeTypes.COMBINATION.get(), input, level);
        if (chosen.isEmpty() || !chosen.get().id().equals(recipeId)) return null;
        ItemStack result = recipe.assemble(input, level.registryAccess());
        var nativeRemaining = recipe.getRemainingItems(input);
        // The native core intentionally discards index zero; do not admit a recipe whose base item has a remainder.
        if (!nativeRemaining.getFirst().isEmpty()) return null;
        var remaining = new ObjectArrayList<ItemStack>(selected.size());
        for (int index = 0; index < selected.size(); index++) remaining.add(nativeRemaining.get(index + 1));
        var expectedOutputs = new ObjectArrayList<ItemStack>();
        expectedOutputs.add(result);
        expectedOutputs.addAll(remaining);
        if (result.isEmpty() || !PackagedIngredientAssignment.outputsMatch(pattern, expectedOutputs)) return null;

        BaseItemStackHandler coreInventory = layout.core().getInventory();
        if (!coreInventory.insertItem(0, assigned.getFirst(), true).isEmpty()) return null;
        for (int index = 0; index < selected.size(); index++) {
            var pedestal = (PedestalTileEntity) level.getBlockEntity(selected.get(index));
            if (pedestal == null || !pedestal.getInventory().insertItem(0, assigned.get(index + 1), true).isEmpty()) {
                return null;
            }
        }

        var progress = new CompoundTag();
        progress.put("layout", positions(layout.pedestals()));
        progress.put("selected", positions(selected));
        progress.put("inputs", saveStacks(assigned, level));
        progress.put("remaining", saveStacks(remaining, level));
        progress.put("result", result.saveOptional(level.registryAccess()));
        return progress;
    }

    @Override
    public ObjectList<BlockPos> occupiedPositions(ServerLevel level, BlockPos position, CompoundTag preparation) {
        var positions = new ObjectArrayList<BlockPos>();
        positions.add(position.immutable());
        ListTag encoded = preparation.getList("layout", Tag.TAG_LONG);
        for (int index = 0; index < encoded.size(); index++) {
            positions.add(BlockPos.of(((LongTag) encoded.get(index)).getAsLong()));
        }
        return positions;
    }

    @Override
    public boolean advance(PackagedMachineOperation operation) {
        Layout layout = layout(operation.level(), operation.position());
        if (layout == null || !matchesPositions(operation.progress().getList("layout", Tag.TAG_LONG), layout.pedestals())) {
            return false;
        }
        CompoundTag progress = operation.progress();
        ObjectList<BlockPos> selected = selected(progress.getList("selected", Tag.TAG_LONG), layout.pedestals());
        if (selected == null) return false;
        ObjectList<ItemStack> assigned = readStacks(operation, progress.getList("inputs", Tag.TAG_COMPOUND));
        ObjectList<ItemStack> remaining = readStacks(operation, progress.getList("remaining", Tag.TAG_COMPOUND));
        ItemStack result = ItemStack.parse(operation.level().registryAccess(), progress.getCompound("result"))
                .orElseThrow(() -> new IllegalArgumentException("Missing Extended Crafting combination output"));
        if (assigned.size() != selected.size() + 1 || remaining.size() != selected.size()) {
            throw new IllegalArgumentException("Invalid persisted Extended Crafting combination inputs");
        }
        ICombinationRecipe recipe = recipe(operation);
        if (!progress.getBoolean("delivered")) {
            return deliver(operation, layout, selected, assigned, recipe, result);
        }
        return collect(operation, layout, selected, assigned, remaining, result);
    }

    private static boolean deliver(PackagedMachineOperation operation, Layout layout,
                                   ObjectList<BlockPos> selected, ObjectList<ItemStack> assigned,
                                   ICombinationRecipe recipe, ItemStack result) {
        if (!empty(operation.level(), layout.core(), layout.pedestals())) return false;
        requireAvailable(operation, assigned);
        BaseItemStackHandler coreInventory = layout.core().getInventory();
        if (!coreInventory.insertItem(0, assigned.getFirst(), true).isEmpty()) return false;
        for (int index = 0; index < selected.size(); index++) {
            var pedestal = (PedestalTileEntity) operation.level().getBlockEntity(selected.get(index));
            if (pedestal == null || !pedestal.getInventory().insertItem(0, assigned.get(index + 1), true).isEmpty()) {
                return false;
            }
        }
        var actualInput = new ObjectArrayList<ItemStack>(assigned);
        if (!matchesNativeRecipe(operation.level(), operation.recipeId(), recipe, actualInput)) {
            throw new IllegalStateException("Extended Crafting combination recipe changed before delivery");
        }
        ItemStack coreInput = assigned.getFirst();
        ItemStack remainder = coreInventory.insertItem(0, coreInput.copy(), false);
        if (!remainder.isEmpty()) throw new IllegalStateException("Extended Crafting core input insertion changed");
        operation.delivered(AEItemKey.of(coreInput), coreInput.getCount());
        for (int index = 0; index < selected.size(); index++) {
            var pedestal = (PedestalTileEntity) operation.level().getBlockEntity(selected.get(index));
            ItemStack stack = assigned.get(index + 1);
            remainder = pedestal.getInventory().insertItem(0, stack.copy(), false);
            int accepted = stack.getCount() - remainder.getCount();
            if (accepted > 0) operation.delivered(AEItemKey.of(stack), accepted);
            if (!remainder.isEmpty()) throw new IllegalStateException("Extended Crafting pedestal insertion changed");
        }
        operation.progress().putBoolean("delivered", true);
        operation.changed();
        return true;
    }

    private static boolean collect(PackagedMachineOperation operation, Layout layout,
                                   ObjectList<BlockPos> selected, ObjectList<ItemStack> assigned,
                                   ObjectList<ItemStack> remaining, ItemStack result) {
        BaseItemStackHandler coreInventory = layout.core().getInventory();
        ItemStack actual = coreInventory.getStackInSlot(0);
        if (actual.isEmpty()) {
            verifyRunningInputs(operation.level(), layout, selected, assigned);
            return false;
        }
        // Until the native core reaches its power cost, slot zero still contains the base input.
        // A recipe may legally produce that same item, so require the pedestal inputs as well
        // before deciding this is still the running phase.
        if (ItemStack.matches(actual, assigned.getFirst()) && matchesRunningInputs(operation.level(), selected, assigned)) {
            return false;
        }
        if (!PackagedOutputMatching.matches(operation, result, actual)) throw new IllegalStateException("Unexpected Extended Crafting combination output");
        for (int index = 0; index < selected.size(); index++) {
            var pedestal = (PedestalTileEntity) operation.level().getBlockEntity(selected.get(index));
            if (pedestal == null || !ItemStack.matches(pedestal.getInventory().getStackInSlot(0), remaining.get(index))) {
                throw new IllegalStateException("Extended Crafting pedestal remainder changed outside this operation");
            }
        }
        for (BlockPos position : layout.pedestals()) {
            if (!selected.contains(position) && !pedestal(operation.level(), position).getInventory().getStackInSlot(0).isEmpty()) {
                throw new IllegalStateException("Unselected Extended Crafting pedestal contains a foreign item");
            }
        }
        ItemStack extracted = coreInventory.extractItem(0, actual.getCount(), false);
        if (extracted.isEmpty()) return false;
        operation.returned(AEItemKey.of(extracted), extracted.getCount());
        for (int index = 0; index < selected.size(); index++) {
            var pedestal = pedestal(operation.level(), selected.get(index));
            ItemStack returned = pedestal.getInventory().extractItem(0, remaining.get(index).getCount(), false);
            if (!returned.isEmpty()) operation.returned(AEItemKey.of(returned), returned.getCount());
        }
        operation.complete();
        return true;
    }

    private static void verifyRunningInputs(ServerLevel level, Layout layout,
                                            ObjectList<BlockPos> selected, ObjectList<ItemStack> assigned) {
        if (!ItemStack.matches(layout.core().getInventory().getStackInSlot(0), assigned.getFirst())) {
            throw new IllegalStateException("Extended Crafting core input changed outside this operation");
        }
        for (int index = 0; index < selected.size(); index++) {
            PedestalTileEntity pedestal = pedestal(level, selected.get(index));
            if (!ItemStack.matches(pedestal.getInventory().getStackInSlot(0), assigned.get(index + 1))) {
                throw new IllegalStateException("Extended Crafting pedestal input changed outside this operation");
            }
        }
    }

    private static boolean matchesRunningInputs(ServerLevel level, ObjectList<BlockPos> selected,
                                                ObjectList<ItemStack> assigned) {
        for (int index = 0; index < selected.size(); index++) {
            if (!(level.getBlockEntity(selected.get(index)) instanceof PedestalTileEntity pedestal) ||
                    !ItemStack.matches(pedestal.getInventory().getStackInSlot(0), assigned.get(index + 1))) {
                return false;
            }
        }
        return true;
    }

    private static ICombinationRecipe recipe(PackagedMachineOperation operation) {
        var holder = operation.level().getRecipeManager().byKey(operation.recipeId());
        if (holder.isEmpty() || !(holder.get().value() instanceof ICombinationRecipe recipe) ||
                recipe.getType() != ModRecipeTypes.COMBINATION.get()) {
            throw new IllegalStateException("Extended Crafting combination recipe disappeared");
        }
        return recipe;
    }

    private static boolean matchesNativeRecipe(ServerLevel level, ResourceLocation recipeId,
                                               ICombinationRecipe recipe, ObjectList<ItemStack> stacks) {
        CraftingInput input = craftingInput(stacks);
        if (!recipe.matches(input, level)) return false;
        var chosen = level.getRecipeManager().getRecipeFor(ModRecipeTypes.COMBINATION.get(), input, level);
        return chosen.isPresent() && chosen.get().id().equals(recipeId);
    }

    private static ObjectList<Ingredient> ingredients(ICombinationRecipe recipe) {
        var ingredients = new ObjectArrayList<Ingredient>();
        ingredients.add(recipe.getInput());
        ingredients.addAll(recipe.getIngredients());
        return ingredients;
    }

    private static CraftingInput craftingInput(List<ItemStack> stacks) {
        return CraftingInput.of(stacks.size(), 1, stacks);
    }

    private static @Nullable Layout layout(ServerLevel level, BlockPos position) {
        if (!level.isLoaded(position) || !(level.getBlockEntity(position) instanceof CraftingCoreTileEntity core)) return null;
        var pedestals = new ObjectArrayList<BlockPos>();
        // The native core scans only its own Y level, in BlockPos iteration order.
        for (BlockPos candidate : BlockPos.betweenClosed(position.offset(-3, 0, -3), position.offset(3, 0, 3))) {
            if (!level.isLoaded(candidate)) return null;
            if (level.getBlockEntity(candidate) instanceof PedestalTileEntity) pedestals.add(candidate.immutable());
        }
        return new Layout(core, pedestals);
    }

    private static boolean empty(ServerLevel level, CraftingCoreTileEntity core, ObjectList<BlockPos> pedestals) {
        if (!core.getInventory().getStackInSlot(0).isEmpty()) return false;
        for (BlockPos position : pedestals) if (!pedestal(level, position).getInventory().getStackInSlot(0).isEmpty()) return false;
        return true;
    }

    private static PedestalTileEntity pedestal(ServerLevel level, BlockPos position) {
        if (level.getBlockEntity(position) instanceof PedestalTileEntity pedestal) return pedestal;
        throw new IllegalStateException("Extended Crafting pedestal disappeared");
    }

    private static ListTag positions(ObjectList<BlockPos> positions) {
        var encoded = new ListTag();
        for (BlockPos position : positions) encoded.add(LongTag.valueOf(position.asLong()));
        return encoded;
    }

    private static boolean matchesPositions(ListTag encoded, ObjectList<BlockPos> actual) {
        if (encoded.size() != actual.size()) return false;
        var expected = new LongOpenHashSet();
        for (BlockPos position : actual) expected.add(position.asLong());
        for (int index = 0; index < encoded.size(); index++) if (!expected.remove(((LongTag) encoded.get(index)).getAsLong())) return false;
        return expected.isEmpty();
    }

    private static @Nullable ObjectList<BlockPos> selected(ListTag encoded, ObjectList<BlockPos> available) {
        var byPosition = new LongOpenHashSet();
        for (BlockPos position : available) byPosition.add(position.asLong());
        var selected = new ObjectArrayList<BlockPos>();
        for (int index = 0; index < encoded.size(); index++) {
            BlockPos position = BlockPos.of(((LongTag) encoded.get(index)).getAsLong());
            if (!byPosition.remove(position.asLong())) return null;
            selected.add(position);
        }
        return selected;
    }

    private static ListTag saveStacks(List<? extends ItemStack> stacks, ServerLevel level) {
        var encoded = new ListTag();
        for (ItemStack stack : stacks) encoded.add(stack.saveOptional(level.registryAccess()));
        return encoded;
    }

    private static ObjectList<ItemStack> readStacks(PackagedMachineOperation operation, ListTag encoded) {
        var stacks = new ObjectArrayList<ItemStack>(encoded.size());
        for (int index = 0; index < encoded.size(); index++) stacks.add(ItemStack.parseOptional(
                operation.level().registryAccess(), encoded.getCompound(index)));
        return stacks;
    }

    private static void requireAvailable(PackagedMachineOperation operation, ObjectList<ItemStack> stacks) {
        var required = new Object2LongLinkedOpenHashMap<AEItemKey>();
        for (ItemStack stack : stacks) required.addTo(AEItemKey.of(stack), stack.getCount());
        for (var entry : required.object2LongEntrySet()) {
            if (operation.available(entry.getKey()).compareTo(BigInteger.valueOf(entry.getLongValue())) < 0) {
                throw new IllegalStateException("Extended Crafting combination plan exceeds remaining provider inputs");
            }
        }
    }

    private record Layout(CraftingCoreTileEntity core, ObjectList<BlockPos> pedestals) {}
}
