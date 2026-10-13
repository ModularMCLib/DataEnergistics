package com.fish_dan_.data_energistics.integration.magic.astral.packaged;

import com.fish_dan_.data_energistics.common.crafting.packaged.recipe.PackagedOutputMatching;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.fluids.FluidActionResult;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.templates.VoidFluidHandler;

import hellfirepvp.astralsorcery.common.ingredient.IngredientBridge;
import hellfirepvp.astralsorcery.common.recipe.altar.AltarCraftingInput;
import hellfirepvp.astralsorcery.common.recipe.altar.AltarRecipe;
import it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.jspecify.annotations.Nullable;

/**
 * Resolves one exact Astral altar cycle from AE inputs without mutating the altar.
 */
final class AstralRecipePlanner {

    private AstralRecipePlanner() {}

    static @Nullable AstralAltarPlan plan(ServerLevel level, AltarRecipe recipe, IPatternDetails pattern,
                                          KeyCounter[] supplied) {
        ObjectList<Group> groups = groups(recipe);
        int cycleSize = 0;
        for (Group group : groups) cycleSize = Math.addExact(cycleSize, group.count());
        if (cycleSize <= 0) return null;

        var totals = new Object2LongLinkedOpenHashMap<AEItemKey>();
        ObjectSet<AEFluidKey> fluidKeys = acceptedFluidKeys(recipe);
        long total = 0;
        for (KeyCounter counter : supplied) {
            for (var entry : counter) {
                if (entry.getLongValue() <= 0) return null;
                if (entry.getKey() instanceof AEFluidKey fluidKey) {
                    if (!fluidKeys.contains(fluidKey)) return null;
                    continue;
                }
                if (!(entry.getKey() instanceof AEItemKey key)) return null;
                total = Math.addExact(total, entry.getLongValue());
                totals.addTo(key, entry.getLongValue());
            }
        }
        if (total != cycleSize) return null;

        ObjectList<ItemStack> assigned = assign(groups, totals);
        if (assigned == null) return null;
        Selection selection = selection(groups, assigned);
        if (!recipe.getGrid().matches(selection.grid(), selection.relay())) return null;

        AltarCraftingInput displayInput = AltarCraftingInput.createDisplay(
                recipe.getFocusConstellation().orElse(null), selection.grid(), selection.relay());
        ObjectList<ItemStack> nativeOutputs = new ObjectArrayList<>(
                recipe.getOutputsForDisplay(displayInput, level.registryAccess()));
        nativeOutputs.addAll(selection.returns());
        if (nativeOutputs.isEmpty() || nativeOutputs.stream().anyMatch(ItemStack::isEmpty)) return null;
        if (!PackagedOutputMatching.matches(pattern, nativeOutputs)) return null;

        return new AstralAltarPlan(selection.grid(), selection.relay(), selection.additional(),
                selection.returns(), selection.slotReturns(), nativeOutputs);
    }

    static @Nullable Selection defaults(AltarRecipe recipe, ObjectList<GenericStack> existing) {
        var available = new Object2LongLinkedOpenHashMap<AEItemKey>();
        for (GenericStack stack : existing) {
            if (stack == null || stack.amount() <= 0 || !(stack.what() instanceof AEItemKey key)) continue;
            available.addTo(key, stack.amount());
        }
        ObjectList<Group> groups = groups(recipe);
        ObjectList<ItemStack> assigned = new ObjectArrayList<>();
        for (Group group : groups) {
            AEItemKey selected = null;
            for (var entry : available.object2LongEntrySet()) {
                if (entry.getLongValue() >= group.count() && group.ingredient().test(entry.getKey().toStack())) {
                    selected = entry.getKey();
                    break;
                }
            }
            if (selected == null) {
                selected = group.ingredient().getItems().findFirst().map(AEItemKey::of).orElse(null);
            }
            if (selected == null) return null;
            if (available.getLong(selected) >= group.count()) available.addTo(selected, -group.count());
            assigned.add(selected.toStack(group.count()));
        }
        return selection(groups, assigned);
    }

    static ObjectSet<AEFluidKey> acceptedFluidKeys(AltarRecipe recipe) {
        var result = new ObjectOpenHashSet<AEFluidKey>();
        for (FluidStack fluid : recipe.getRequiredFluid()) {
            AEFluidKey key = AEFluidKey.of(fluid);
            if (key != null) result.add(key);
        }
        return result;
    }

    static long requiredFluidAmount(AltarRecipe recipe, AEFluidKey requiredKey) {
        long result = 0;
        for (FluidStack fluid : recipe.getRequiredFluid()) {
            if (fluid.isEmpty()) continue;
            AEFluidKey key = AEFluidKey.of(fluid);
            if (requiredKey.equals(key)) result = Math.addExact(result, fluid.getAmount());
        }
        return result;
    }

    static ObjectList<Group> groups(AltarRecipe recipe) {
        ObjectList<Group> result = new ObjectArrayList<>();
        var grid = recipe.getGrid().getInputs();
        for (int index = 0; index < grid.size(); index++) {
            IngredientBridge ingredient = grid.get(index);
            if (!ingredient.isEmpty()) result.add(new Group(0, index, 1, ingredient));
        }
        var relay = recipe.getGrid().getRelayInputs();
        for (int index = 0; index < relay.size(); index++) {
            IngredientBridge ingredient = relay.get(index);
            if (!ingredient.isEmpty()) result.add(new Group(1, index, 1, ingredient));
        }
        ObjectList<SizedIngredient> additional = new ObjectArrayList<>(recipe.getRequiredAdditionalInputs());
        for (int index = 0; index < additional.size(); index++) {
            SizedIngredient ingredient = additional.get(index);
            if (!ingredient.ingredient().isEmpty() && ingredient.count() > 0) {
                result.add(new Group(2, index, ingredient.count(), IngredientBridge.of(ingredient.ingredient())));
            }
        }
        return result;
    }

    private static @Nullable ObjectList<ItemStack> assign(ObjectList<Group> groups,
                                                          Object2LongLinkedOpenHashMap<AEItemKey> available) {
        ObjectList<ItemStack> result = new ObjectArrayList<>();
        int[] budget = { 4096 };
        return assign(groups, available, result, budget) ? result : null;
    }

    private static boolean assign(ObjectList<Group> groups, Object2LongLinkedOpenHashMap<AEItemKey> available,
                                  ObjectList<ItemStack> assigned, int[] budget) {
        if (assigned.size() == groups.size()) return true;
        if (--budget[0] < 0) return false;
        Group group = groups.get(assigned.size());
        for (var entry : available.object2LongEntrySet()) {
            if (entry.getLongValue() < group.count() || !group.ingredient().test(entry.getKey().toStack())) continue;
            entry.setValue(entry.getLongValue() - group.count());
            assigned.add(entry.getKey().toStack(group.count()));
            if (assign(groups, available, assigned, budget)) return true;
            assigned.removeLast();
            entry.setValue(entry.getLongValue() + group.count());
        }
        return false;
    }

    private static Selection selection(ObjectList<Group> groups, ObjectList<ItemStack> assigned) {
        ObjectList<ItemStack> grid = new ObjectArrayList<>();
        ObjectList<ItemStack> relay = new ObjectArrayList<>();
        for (int index = 0; index < 9; index++) grid.add(ItemStack.EMPTY);
        for (int index = 0; index < 25; index++) relay.add(ItemStack.EMPTY);
        ObjectList<ItemStack> additional = new ObjectArrayList<>();
        ObjectList<ItemStack> returns = new ObjectArrayList<>();
        ObjectList<ItemStack> slotReturns = new ObjectArrayList<>();
        for (int index = 0; index < 34; index++) slotReturns.add(ItemStack.EMPTY);
        for (int index = 0; index < groups.size(); index++) {
            Group group = groups.get(index);
            ItemStack stack = assigned.get(index).copy();
            if (group.section() == 0) {
                grid.set(group.index(), stack);
                addReturn(returns, slotReturns, group.index(), group.ingredient(), stack);
            } else if (group.section() == 1) {
                relay.set(group.index(), stack);
                addReturn(returns, slotReturns, group.index() + 9, group.ingredient(), stack);
            } else {
                additional.add(stack);
            }
        }
        return new Selection(grid, relay, additional, returns, slotReturns);
    }

    private static void addReturn(ObjectList<ItemStack> returns, ObjectList<ItemStack> slotReturns, int slot,
                                  IngredientBridge ingredient, ItemStack stack) {
        ItemStack remainder = remainder(ingredient, stack);
        if (!remainder.isEmpty()) {
            returns.add(remainder);
            slotReturns.set(slot, remainder);
        }
    }

    private static ItemStack remainder(IngredientBridge ingredient, ItemStack stack) {
        if (ingredient.getIngredientType() == IngredientBridge.Type.ITEM) {
            return stack.getCraftingRemainingItem();
        }
        FluidActionResult result = FluidUtil.tryEmptyContainer(stack.copy(), VoidFluidHandler.INSTANCE,
                ingredient.getFluidIngredient().amount(), null, true);
        return result.isSuccess() ? result.getResult() : ItemStack.EMPTY;
    }

    record Group(int section, int index, int count, IngredientBridge ingredient) {}

    record Selection(ObjectList<ItemStack> grid, ObjectList<ItemStack> relay,
                     ObjectList<ItemStack> additional, ObjectList<ItemStack> returns,
                     ObjectList<ItemStack> slotReturns) {}

    record AstralAltarPlan(ObjectList<ItemStack> grid, ObjectList<ItemStack> relay,
                           ObjectList<ItemStack> additional, ObjectList<ItemStack> returns,
                           ObjectList<ItemStack> slotReturns,
                           ObjectList<ItemStack> outputs) {}
}
