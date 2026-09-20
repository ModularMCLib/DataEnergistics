package com.fish_dan_.data_energistics.integration.magic.botania.packaged;

import com.fish_dan_.data_energistics.common.crafting.packaged.recipe.PackagedIngredientAssignment;
import com.fish_dan_.data_energistics.common.crafting.packaged.recipe.PackagedOutputMatching;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.entity.BlockEntity;

import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import org.jspecify.annotations.Nullable;
import vazkii.botania.api.recipe.ElvenTradeRecipe;
import vazkii.botania.api.recipe.ManaInfusionRecipe;
import vazkii.botania.api.recipe.PetalApothecaryRecipe;
import vazkii.botania.api.recipe.RunicAltarRecipe;
import vazkii.botania.api.recipe.TerrestrialAgglomerationRecipe;
import vazkii.botania.common.block.block_entity.mana.ManaPoolBlockEntity;
import vazkii.botania.common.crafting.BotaniaRecipeTypes;
import vazkii.botania.common.crafting.recipe.RecipeUtils;

/** Exact one-cycle inputs, including closing reagent/water, and the native output multiset. */
record BotaniaRecipePlan(ObjectList<ItemStack> inputs, ObjectList<ItemStack> outputs,
                         int ingredientCount, boolean waterBucket, long cycles) {

    static @Nullable BotaniaRecipePlan prepare(ServerLevel level, BlockEntity machine, RecipeHolder<?> holder,
                                               IPatternDetails pattern, KeyCounter[] supplied, boolean waterBucket) {
        Recipe<?> recipe = holder.value();
        var ingredients = new ObjectArrayList<>(recipe.getIngredients());
        if (recipe instanceof RunicAltarRecipe rune) ingredients.addAll(rune.getCatalysts());
        int ingredientCount = ingredients.size();
        if (recipe instanceof PetalApothecaryRecipe petal) ingredients.add(petal.getReagent());
        if (recipe instanceof RunicAltarRecipe rune) ingredients.add(rune.getReagent());
        if (waterBucket) ingredients.add(Ingredient.of(Items.WATER_BUCKET));
        if (ingredients.isEmpty() || ingredients.size() > 64) return null;
        long total = 0;
        var counts = new KeyCounter();
        for (var counter : supplied) {
            for (var entry : counter) {
                if (!(entry.getKey() instanceof AEItemKey) || entry.getLongValue() <= 0 || entry.getLongValue() > Long.MAX_VALUE - total) return null;
                total += entry.getLongValue();
                counts.add(entry.getKey(), entry.getLongValue());
            }
        }
        if (total == 0 || total % ingredients.size() != 0) return null;
        long cycles = total / ingredients.size();
        var normalized = new KeyCounter();
        for (var entry : counts) {
            if (entry.getLongValue() % cycles != 0) return null;
            normalized.add(entry.getKey(), entry.getLongValue() / cycles);
        }
        var assigned = PackagedIngredientAssignment.match(ingredients, new KeyCounter[] { normalized });
        if (assigned == null) return null;
        var outputs = nativeOutputs(level, machine, holder, assigned.subList(0, ingredientCount));
        if (outputs == null) return null;
        if (waterBucket) outputs.add(new ItemStack(Items.BUCKET));
        if (!matchesOutputs(pattern, outputs, cycles)) return null;
        return new BotaniaRecipePlan(assigned, outputs, ingredientCount, waterBucket, cycles);
    }

    static @Nullable ObjectList<ItemStack> nativeOutputs(ServerLevel level, BlockEntity machine,
                                                         RecipeHolder<?> holder, ObjectList<ItemStack> ingredients) {
        var input = RecipeUtils.getInputFromListWithoutUnstacking(ingredients);
        Recipe<?> recipe = holder.value();
        var outputs = new ObjectArrayList<ItemStack>();
        if (recipe instanceof PetalApothecaryRecipe petal) {
            if (!petal.matches(input, level)) return null;
            var selected = level.getRecipeManager().getRecipeFor(BotaniaRecipeTypes.PETAL_APOTHECARY_TYPE, input, level);
            if (selected.isEmpty() || !selected.get().id().equals(holder.id())) return null;
            outputs.add(petal.assemble(input, level.registryAccess()));
        } else if (recipe instanceof RunicAltarRecipe rune) {
            if (!rune.matches(input, level) || rune.getMana() <= 0) return null;
            var selected = level.getRecipeManager().getRecipeFor(BotaniaRecipeTypes.RUNIC_ALTAR_TYPE, input, level);
            if (selected.isEmpty() || !selected.get().id().equals(holder.id())) return null;
            outputs.add(rune.assemble(input, level.registryAccess()));
            for (var remaining : rune.getRemainingItems(input)) if (!remaining.isEmpty()) outputs.add(remaining.copy());
        } else if (recipe instanceof TerrestrialAgglomerationRecipe terra) {
            if (!terra.matches(input, level) || terra.getMana() <= 0) return null;
            var selected = level.getRecipeManager().getRecipeFor(BotaniaRecipeTypes.TERRA_PLATE_TYPE, input, level);
            if (selected.isEmpty() || !selected.get().id().equals(holder.id())) return null;
            outputs.add(terra.assemble(input, level.registryAccess()));
        } else if (recipe instanceof ManaInfusionRecipe mana && machine instanceof ManaPoolBlockEntity pool) {
            if (ingredients.size() != 1 || !mana.matches(ingredients.getFirst())) return null;
            var selected = pool.getMatchingRecipe(ingredients.getFirst(), level.getBlockState(pool.getBlockPos().below()));
            if (selected == null || !selected.id().equals(holder.id())) return null;
            outputs.add(mana.getRecipeOutput(level.registryAccess(), ingredients.getFirst().copy()));
        } else if (recipe instanceof ElvenTradeRecipe trade) {
            var assembled = trade.tryAssemble(input, level.registryAccess());
            if (assembled.isEmpty()) return null;
            int consumed = assembled.get().matchedInputSlots().values().intStream().sum();
            if (consumed != ingredients.size()) return null;
            for (var candidate : level.getRecipeManager().getAllRecipesFor(BotaniaRecipeTypes.ELVEN_TRADE_TYPE)) {
                if (candidate.id().equals(holder.id())) continue;
                var alternative = candidate.value().tryAssemble(input, level.registryAccess());
                if (alternative.isPresent() && alternative.get().compareTo(assembled.get()) <= 0) return null;
            }
            assembled.get().outputs().forEach(stack -> outputs.add(stack.copy()));
        } else return null;
        return outputs.isEmpty() || outputs.stream().anyMatch(ItemStack::isEmpty) ? null : outputs;
    }

    static boolean matchesOutputs(IPatternDetails pattern, ObjectList<ItemStack> outputs, long cycles) {
        var expected = new Object2LongOpenHashMap<AEItemKey>();
        for (ItemStack stack : outputs) {
            if (stack.isEmpty() || cycles > Long.MAX_VALUE / stack.getCount()) return false;
            var key = AEItemKey.of(stack);
            long amount = cycles * stack.getCount();
            if (amount > Long.MAX_VALUE - expected.getLong(key)) return false;
            expected.addTo(key, amount);
        }
        return PackagedOutputMatching.matches(pattern, expected);
    }
}
