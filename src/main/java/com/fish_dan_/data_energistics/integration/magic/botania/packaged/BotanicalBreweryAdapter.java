package com.fish_dan_.data_energistics.integration.magic.botania.packaged;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.crafting.packaged.PackagedMachineAdapter;
import com.fish_dan_.data_energistics.api.crafting.packaged.PackagedMachineOperation;
import com.fish_dan_.data_energistics.common.crafting.packaged.execution.PackagedEntityCapture;
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
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.jspecify.annotations.Nullable;
import vazkii.botania.api.recipe.BotanicalBreweryRecipe;
import vazkii.botania.common.block.block_entity.BotanicalBreweryBlockEntity;
import vazkii.botania.common.crafting.recipe.RecipeUtils;

import java.math.BigInteger;

/** Runs Botania's native brewery item pickup and mana-gated craft cycle for packaged inputs. */
public final class BotanicalBreweryAdapter implements PackagedMachineAdapter {

    private static final ResourceLocation RECIPE_TYPE = BotanicalBreweryRecipe.TYPE_ID;

    @Override
    public ResourceLocation id() {
        return Data_Energistics.id("botania_botanical_brewery");
    }

    @Override
    public ObjectSet<ResourceLocation> recipeTypes() {
        return ObjectSet.of(RECIPE_TYPE);
    }

    @Override
    public boolean recognizes(ServerLevel level, BlockPos position) {
        return level.isLoaded(position) && level.getBlockEntity(position) instanceof BotanicalBreweryBlockEntity;
    }

    @Override
    public @Nullable ItemStack completeEncoding(ServerLevel level, ResourceLocation recipeId, ItemStack encodedPattern) {
        var holder = level.getRecipeManager().byKey(recipeId);
        return holder.isPresent() ? BotanicalBreweryPatternEncoding.complete(level, holder.get(), encodedPattern) : null;
    }

    @Override
    public @Nullable CompoundTag prepare(ServerLevel level, BlockPos position, Direction face,
                                         ResourceLocation recipeId, IPatternDetails pattern, KeyCounter[] inputs) {
        if (!recognizes(level, position) || !(level.getBlockEntity(position) instanceof BotanicalBreweryBlockEntity brewery) || !ready(brewery))
            return null;
        var holder = level.getRecipeManager().byKey(recipeId);
        if (holder.isEmpty() || !(holder.get().value() instanceof BotanicalBreweryRecipe recipe)) return null;

        var plan = plan(level, recipe, pattern, inputs);
        if (plan == null) return null;
        var progress = new CompoundTag();
        var encodedInputs = new ListTag();
        for (var input : plan.inputs()) encodedInputs.add(input.save(level.registryAccess()));
        var encodedOutputs = new ListTag();
        for (var output : plan.outputs()) encodedOutputs.add(output.save(level.registryAccess()));
        progress.put("inputs", encodedInputs);
        progress.put("outputs", encodedOutputs);
        progress.putLong("cycles", plan.cycles());
        return progress;
    }

    @Override
    public boolean advance(PackagedMachineOperation operation) {
        if (!(operation.level().getBlockEntity(operation.position()) instanceof BotanicalBreweryBlockEntity brewery)) return false;
        var progress = operation.progress();
        long cycles = progress.getLong("cycles");
        if (cycles <= 0) throw new IllegalArgumentException("Invalid Botania brewery cycle count");
        if (progress.getBoolean("delivered")) {
            if (!BotaniaOperationItems.collect(operation)) return false;
            cycles--;
            progress.putLong("cycles", cycles);
            progress.putBoolean("delivered", false);
            progress.remove("entities");
            progress.remove("recovered");
            operation.changed();
            if (cycles == 0) operation.complete();
            return true;
        }
        if (!ready(brewery)) return false;
        // Botania clears the physical slots when the brew finishes, but keeps the old recipe
        // reference until its next native tick. Clear that derived state before inserting the
        // next operation so a same-tick dispatch is not rejected by addItem().
        if (brewery.recipe != null) {
            brewery.recipe = null;
            brewery.signal = 0;
            brewery.setChanged();
        }
        var inputs = read(operation, "inputs");
        var required = new KeyCounter();
        for (var input : inputs) required.add(AEItemKey.of(input), input.getCount());
        for (var entry : required) {
            if (operation.available(entry.getKey()).compareTo(BigInteger.valueOf(entry.getLongValue())) < 0)
                throw new IllegalStateException("Missing owned Botania brewery input");
        }
        var holder = operation.level().getRecipeManager().byKey(operation.recipeId());
        if (holder.isEmpty() || !(holder.get().value() instanceof BotanicalBreweryRecipe recipe))
            throw new IllegalStateException("Botania brewery recipe disappeared");
        var nativeInput = RecipeUtils.getInputFromListWithoutUnstacking(inputs);
        if (!recipe.matches(nativeInput, operation.level())) return false;

        for (var input : inputs) {
            ItemEntity entity = BotaniaOperationItems.spawn(operation, input, operation.position().getCenter());
            boolean[] accepted = { false };
            PackagedEntityCapture.run(operation.level(), operation.id(), () -> accepted[0] = brewery.addItem(null, entity.getItem(), null));
            if (!accepted[0] || !entity.getItem().isEmpty()) throw new IllegalStateException("Botania brewery refused a planned input");
            entity.discard();
        }
        if (brewery.recipe != recipe || brewery.getManaCost() < 0) throw new IllegalStateException("Botania brewery selected another recipe");
        progress.putBoolean("delivered", true);
        operation.changed();
        return true;
    }

    private static @Nullable Plan plan(ServerLevel level, BotanicalBreweryRecipe recipe,
                                       IPatternDetails pattern, KeyCounter[] supplied) {
        var requirements = new ObjectArrayList<Ingredient>();
        requirements.add(RecipeUtils.getBrewContainerIngredient());
        requirements.addAll(recipe.getIngredients());
        long total = 0;
        var counts = new KeyCounter();
        for (var counter : supplied) {
            for (var entry : counter) {
                if (!(entry.getKey() instanceof AEItemKey) || entry.getLongValue() <= 0 || entry.getLongValue() > Long.MAX_VALUE - total)
                    return null;
                total += entry.getLongValue();
                counts.add(entry.getKey(), entry.getLongValue());
            }
        }
        int cycleSize = requirements.size();
        if (cycleSize <= 1 || total == 0 || total % cycleSize != 0) return null;
        long cycles = total / cycleSize;
        var normalized = new KeyCounter();
        for (var entry : counts) {
            if (entry.getLongValue() % cycles != 0) return null;
            normalized.add(entry.getKey(), entry.getLongValue() / cycles);
        }
        var assigned = PackagedIngredientAssignment.match(requirements, new KeyCounter[] { normalized });
        if (assigned == null) return null;
        var nativeInput = RecipeUtils.getInputFromListWithoutUnstacking(assigned);
        if (!recipe.matches(nativeInput, level)) return null;
        var outputs = new ObjectArrayList<ItemStack>();
        outputs.add(recipe.getOutput(assigned.getFirst()));
        for (var remaining : recipe.getRemainingItems(nativeInput)) if (!remaining.isEmpty()) outputs.add(remaining.copy());
        if (outputs.stream().anyMatch(ItemStack::isEmpty) || !matchesOutputs(pattern, outputs, cycles)) return null;
        return new Plan(assigned, outputs, cycles);
    }

    private static boolean matchesOutputs(IPatternDetails pattern, ObjectList<ItemStack> outputs, long cycles) {
        var expected = new Object2LongOpenHashMap<AEItemKey>();
        for (var output : outputs) {
            if (cycles > Long.MAX_VALUE / output.getCount()) return false;
            expected.addTo(AEItemKey.of(output), cycles * output.getCount());
        }
        return PackagedOutputMatching.matches(pattern, expected);
    }

    private static boolean ready(BotanicalBreweryBlockEntity brewery) {
        for (int slot = 0; slot < brewery.inventorySize(); slot++) if (!brewery.getItemHandler().getItem(slot).isEmpty()) return false;
        return true;
    }

    private static ObjectList<ItemStack> read(PackagedMachineOperation operation, String name) {
        var list = operation.progress().getList(name, Tag.TAG_COMPOUND);
        var result = new ObjectArrayList<ItemStack>();
        for (int index = 0; index < list.size(); index++) {
            result.add(ItemStack.parse(operation.level().registryAccess(), list.getCompound(index))
                    .orElseThrow(() -> new IllegalArgumentException("Invalid Botania brewery persisted item")));
        }
        return result;
    }

    private record Plan(ObjectList<ItemStack> inputs, ObjectList<ItemStack> outputs, long cycles) {}
}
