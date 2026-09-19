package com.fish_dan_.data_energistics.integration.crafting.packaged.mekanismmore;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.crafting.Recipe;
import net.neoforged.neoforge.fluids.FluidStack;

import it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import me.ramidzkh.mekae2.ae2.MekanismKey;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.recipes.ChemicalChemicalToChemicalRecipe;
import mekanism.api.recipes.ChemicalToChemicalRecipe;
import mekanism.api.recipes.ElectrolysisRecipe;
import mekanism.api.recipes.NucleosynthesizingRecipe;
import mekanism.api.recipes.RotaryRecipe;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

/** One native cycle plus the exact number of cycles represented by the input envelope. */
record LargeMachineRecipePlan(List<GenericStack> inputs, List<GenericStack> outputs, long cycles) {

    static @Nullable LargeMachineRecipePlan prepare(ServerLevel level, LargeMachineKind.Layout layout,
                                                    ResourceLocation recipeId, IPatternDetails pattern, KeyCounter[] counters) {
        var supplied = new Object2LongLinkedOpenHashMap<AEKey>();
        try {
            for (var counter : counters) for (var entry : counter) {
                if (entry.getLongValue() <= 0) return null;
                supplied.merge(entry.getKey(), entry.getLongValue(), Math::addExact);
            }
            var holder = level.getRecipeManager().byKey(recipeId);
            if (holder.isEmpty()) return null;
            var recipe = holder.get().value();
            if (!layout.tile().getRecipeType().getRegistryName().equals(
                    BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType())))
                return null;
            // Different slots may intentionally require the same AE key; assignment accounts for the sum.
            for (var first : supplied.keySet()) for (var second : supplied.keySet()) {
                var unit = unit(recipe, first, second, layout.fluidToChemical());
                if (unit == null) continue;
                var expected = totals(unit.outputs());
                var declared = new Object2LongLinkedOpenHashMap<AEKey>();
                for (var output : pattern.getOutputs()) {
                    if (output.amount() <= 0) return null;
                    declared.merge(output.what(), output.amount(), Math::addExact);
                }
                if (!declared.keySet().equals(expected.keySet())) continue;
                long cycles = 0;
                boolean matched = true;
                for (var output : expected.entrySet()) {
                    long amount = declared.getLong(output.getKey());
                    if (amount % output.getValue() != 0 || cycles != 0 && cycles != amount / output.getValue()) {
                        matched = false;
                        break;
                    }
                    cycles = amount / output.getValue();
                }
                if (!matched || cycles <= 0) continue;
                var required = totals(unit.inputs());
                final long count = cycles;
                required.replaceAll((key, amount) -> Math.multiplyExact(amount, count));
                if (!supplied.equals(required)) continue;
                // Query the same ordered native recipe cache before any physical input is touched.
                var selected = layout.tile().getRecipeType().findFirst(level,
                        candidate -> unit(candidate, first, second, layout.fluidToChemical()) != null);
                if (selected != recipe) continue;
                return new LargeMachineRecipePlan(unit.inputs(), unit.outputs(), cycles);
            }
        } catch (ArithmeticException exception) {
            return null;
        }
        return null;
    }

    static @Nullable LargeMachineRecipePlan unit(Recipe<?> recipe, AEKey first, AEKey second, boolean fluidToChemical) {
        if (recipe instanceof ChemicalChemicalToChemicalRecipe binary && first instanceof MekanismKey left && second instanceof MekanismKey right) {
            var a = binary.getLeftInput().getMatchingInstance(left.withAmount(Long.MAX_VALUE));
            var b = binary.getRightInput().getMatchingInstance(right.withAmount(Long.MAX_VALUE));
            if (a.isEmpty() || b.isEmpty() || !binary.test(a, b)) return null;
            var output = binary.getOutput(a, b);
            return output.isEmpty() ? null : new LargeMachineRecipePlan(List.of(chemical(a), chemical(b)), List.of(chemical(output)), 1);
        }
        if (recipe instanceof ChemicalToChemicalRecipe single && first instanceof MekanismKey key) {
            var input = single.getInput().getMatchingInstance(key.withAmount(Long.MAX_VALUE));
            if (input.isEmpty() || !single.test(input)) return null;
            var output = single.getOutput(input);
            return output.isEmpty() ? null : new LargeMachineRecipePlan(List.of(chemical(input)), List.of(chemical(output)), 1);
        }
        if (recipe instanceof ElectrolysisRecipe separating && first instanceof AEFluidKey key) {
            var input = separating.getInput().getMatchingInstance(key.toStack(Integer.MAX_VALUE));
            if (input.isEmpty() || !separating.test(input)) return null;
            var output = separating.getOutput(input);
            return output.left().isEmpty() || output.right().isEmpty() ? null :
                    new LargeMachineRecipePlan(List.of(fluid(input)), List.of(chemical(output.left()), chemical(output.right())), 1);
        }
        if (recipe instanceof RotaryRecipe rotary) {
            if (fluidToChemical && rotary.hasFluidToChemical() && first instanceof AEFluidKey key) {
                var input = rotary.getFluidInput().getMatchingInstance(key.toStack(Integer.MAX_VALUE));
                if (input.isEmpty() || !rotary.test(input)) return null;
                var output = rotary.getChemicalOutput(input);
                return output.isEmpty() ? null : new LargeMachineRecipePlan(List.of(fluid(input)), List.of(chemical(output)), 1);
            }
            if (!fluidToChemical && rotary.hasChemicalToFluid() && first instanceof MekanismKey key) {
                var input = rotary.getChemicalInput().getMatchingInstance(key.withAmount(Long.MAX_VALUE));
                if (input.isEmpty() || !rotary.test(input)) return null;
                var output = rotary.getFluidOutput(input);
                return output.isEmpty() ? null : new LargeMachineRecipePlan(List.of(chemical(input)), List.of(fluid(output)), 1);
            }
        }
        if (recipe instanceof NucleosynthesizingRecipe nuclear && first instanceof AEItemKey item && second instanceof MekanismKey gas) {
            var input = nuclear.getItemInput().getMatchingInstance(item.toStack(Integer.MAX_VALUE));
            var chemical = nuclear.getChemicalInput().getMatchingInstance(gas.withAmount(Long.MAX_VALUE));
            if (input.isEmpty() || chemical.isEmpty() || !nuclear.test(input, chemical)) return null;
            var output = nuclear.getOutput(input, chemical);
            if (output.isEmpty()) return null;
            long chemicalAmount = Math.multiplyExact(chemical.getAmount(), nuclear.perTickUsage() ? nuclear.getDuration() : 1);
            if (chemicalAmount <= 0) return null;
            return new LargeMachineRecipePlan(List.of(new GenericStack(AEItemKey.of(input), input.getCount()),
                    new GenericStack(gas, chemicalAmount)), List.of(new GenericStack(AEItemKey.of(output), output.getCount())), 1);
        }
        return null;
    }

    private static GenericStack chemical(ChemicalStack stack) {
        return new GenericStack(MekanismKey.of(stack), stack.getAmount());
    }

    private static GenericStack fluid(FluidStack stack) {
        return new GenericStack(AEFluidKey.of(stack), stack.getAmount());
    }

    static Map<AEKey, Long> totals(List<GenericStack> stacks) {
        var result = new Object2LongLinkedOpenHashMap<AEKey>();
        for (var stack : stacks) result.merge(stack.what(), stack.amount(), Math::addExact);
        return result;
    }

    CompoundTag save(HolderLookup.Provider registries) {
        var tag = new CompoundTag();
        tag.put("machine_inputs", saveStacks(inputs, registries));
        tag.put("machine_outputs", saveStacks(outputs, registries));
        tag.putLong("cycles", cycles);
        return tag;
    }

    private static ListTag saveStacks(List<GenericStack> stacks, HolderLookup.Provider registries) {
        var list = new ListTag();
        for (var stack : stacks) {
            var tag = new CompoundTag();
            tag.put("key", stack.what().toTagGeneric(registries));
            tag.putLong("amount", stack.amount());
            list.add(tag);
        }
        return list;
    }

    static LargeMachineRecipePlan load(CompoundTag tag, HolderLookup.Provider registries) {
        long cycles = tag.getLong("cycles");
        if (cycles <= 0) throw new IllegalArgumentException("Invalid remaining Mekanism cycles");
        return new LargeMachineRecipePlan(loadStacks(tag, "machine_inputs", registries), loadStacks(tag, "machine_outputs", registries), cycles);
    }

    private static List<GenericStack> loadStacks(CompoundTag tag, String name, HolderLookup.Provider registries) {
        var entries = tag.getList(name, Tag.TAG_COMPOUND);
        if (entries.isEmpty() || entries.size() > 2) throw new IllegalArgumentException("Invalid Mekanism resource port list");
        var stacks = new ObjectArrayList<GenericStack>();
        for (int i = 0; i < entries.size(); i++) {
            var entry = entries.getCompound(i);
            var key = AEKey.fromTagGeneric(registries, entry.getCompound("key"));
            long amount = entry.getLong("amount");
            if (key == null || amount <= 0) throw new IllegalArgumentException("Invalid Mekanism resource key or amount");
            stacks.add(new GenericStack(key, amount));
        }
        return List.copyOf(stacks);
    }
}
