package com.fish_dan_.data_energistics.integration.magic.malum.packaged;

import com.fish_dan_.data_energistics.common.crafting.packaged.recipe.PackagedOutputMatching;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.crafting.SizedIngredient;

import com.sammy.malum.common.recipe.SpiritFocusingRecipe;
import com.sammy.malum.common.recipe.SpiritInfusionRecipe;
import com.sammy.malum.core.systems.recipe.SpiritBasedRecipeInput;
import com.sammy.malum.core.systems.recipe.SpiritIngredient;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import org.jspecify.annotations.Nullable;

import java.util.List;

/** Sized requirements stay in physical stacks, preserving component identity and native count checks. */
record MalumRecipePlan(ItemStack main, ObjectList<ItemStack> spirits, ObjectList<ItemStack> extras,
                       ItemStack output, long cycles, boolean installedMain) {

    static @Nullable MalumRecipePlan prepare(ServerLevel level, Object recipe, ItemStack installed,
                                             IPatternDetails pattern, KeyCounter[] supplied) {
        var requirements = new ObjectArrayList<SizedIngredient>();
        List<SpiritIngredient> spirits;
        boolean installedMain = recipe instanceof SpiritFocusingRecipe && !installed.isEmpty();
        if (recipe instanceof SpiritInfusionRecipe infusion) {
            requirements.add(infusion.input);
            spirits = infusion.spirits;
        } else if (recipe instanceof SpiritFocusingRecipe focusing) {
            if (!installedMain || !focusing.input.test(installed)) return null;
            spirits = focusing.spirits;
        } else return null;
        for (SpiritIngredient spirit : spirits) {
            ItemStack stack = spirit.asItemStack();
            if (stack.isEmpty() || stack.getCount() <= 0) return null;
            requirements.add(new SizedIngredient(Ingredient.of(stack.getItem()), stack.getCount()));
        }
        if (recipe instanceof SpiritInfusionRecipe infusion) requirements.addAll(infusion.extraInputs);
        if (requirements.isEmpty() || requirements.size() > 32) return null;
        long cycleSize = 0;
        for (SizedIngredient requirement : requirements) {
            if (requirement.count() <= 0 || requirement.count() > 64) return null;
            cycleSize += requirement.count();
        }
        long total = 0;
        var counts = new KeyCounter();
        for (var counter : supplied) {
            for (var entry : counter) {
                if (!(entry.getKey() instanceof AEItemKey) || entry.getLongValue() <= 0 || entry.getLongValue() > Long.MAX_VALUE - total) return null;
                total += entry.getLongValue();
                counts.add(entry.getKey(), entry.getLongValue());
            }
        }
        if (total == 0 || total % cycleSize != 0) return null;
        long cycles = total / cycleSize;
        var keys = new ObjectArrayList<AEItemKey>();
        LongList remaining = new LongArrayList();
        for (var entry : counts) {
            if (entry.getLongValue() % cycles != 0) return null;
            keys.add((AEItemKey) entry.getKey());
            remaining.add(entry.getLongValue() / cycles);
        }
        var assigned = new ObjectArrayList<ItemStack>();
        if (!assign(requirements, keys, remaining, assigned, new int[] { 4096 })) return null;
        int spiritOffset = installedMain ? 0 : 1;
        ItemStack main = installedMain ? installed.copy() : assigned.getFirst();
        var spiritStacks = new ObjectArrayList<>(assigned.subList(spiritOffset, spiritOffset + spirits.size()));
        var extras = new ObjectArrayList<>(assigned.subList(spiritOffset + spirits.size(), assigned.size()));
        var input = new SpiritBasedRecipeInput(main.copy(), spiritStacks);
        ItemStack output;
        if (recipe instanceof SpiritInfusionRecipe infusion) {
            if (!infusion.matches(input, level)) return null;
            output = infusion.getOutput(level, main.copy());
        } else {
            var focusing = (SpiritFocusingRecipe) recipe;
            if (!focusing.matches(input, level)) return null;
            output = focusing.output.copy();
        }
        if (output.isEmpty() || cycles > Long.MAX_VALUE / output.getCount() || pattern.getOutputs().size() != 1) return null;
        if (!PackagedOutputMatching.matches(pattern, output, cycles * output.getCount())) return null;
        return new MalumRecipePlan(main, spiritStacks, extras, output, cycles, installedMain);
    }

    private static boolean assign(ObjectList<SizedIngredient> requirements, ObjectList<AEItemKey> keys,
                                  LongList remaining, ObjectList<ItemStack> assigned, int[] budget) {
        if (assigned.size() == requirements.size()) return remaining.longStream().allMatch(count -> count == 0);
        if (--budget[0] < 0) return false;
        SizedIngredient requirement = requirements.get(assigned.size());
        for (int index = 0; index < keys.size(); index++) {
            if (remaining.getLong(index) < requirement.count()) continue;
            ItemStack stack = keys.get(index).toStack(requirement.count());
            if (!requirement.test(stack)) continue;
            remaining.set(index, remaining.getLong(index) - requirement.count());
            assigned.add(stack);
            if (assign(requirements, keys, remaining, assigned, budget)) return true;
            assigned.removeLast();
            remaining.set(index, remaining.getLong(index) + requirement.count());
        }
        return false;
    }
}
