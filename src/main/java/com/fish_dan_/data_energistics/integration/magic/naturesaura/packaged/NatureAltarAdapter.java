package com.fish_dan_.data_energistics.integration.magic.naturesaura.packaged;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.crafting.packaged.PackagedMachineAdapter;
import com.fish_dan_.data_energistics.api.crafting.packaged.PackagedMachineOperation;
import com.fish_dan_.data_energistics.common.crafting.packaged.recipe.PackagedOutputMatching;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

import de.ellpeck.naturesaura.blocks.ModBlocks;
import de.ellpeck.naturesaura.blocks.tiles.BlockEntityNatureAltar;
import de.ellpeck.naturesaura.recipes.AltarRecipe;
import de.ellpeck.naturesaura.recipes.ModRecipes;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.jspecify.annotations.Nullable;

import java.math.BigInteger;

/** Feeds one real Nature's Aura altar cycle and collects the native result from its single slot. */
public final class NatureAltarAdapter implements PackagedMachineAdapter {

    private static final ResourceLocation RECIPE_TYPE = ResourceLocation.fromNamespaceAndPath("naturesaura", "altar");

    @Override
    public ResourceLocation id() {
        return Data_Energistics.id("naturesaura_altar");
    }

    @Override
    public ObjectSet<ResourceLocation> recipeTypes() {
        return ObjectSet.of(RECIPE_TYPE);
    }

    @Override
    public boolean recognizes(ServerLevel level, BlockPos position) {
        return level.isLoaded(position) && level.getBlockState(position).is(ModBlocks.NATURE_ALTAR);
    }

    @Override
    public @Nullable CompoundTag prepare(ServerLevel level, BlockPos position, Direction face,
                                         ResourceLocation recipeId, IPatternDetails pattern, KeyCounter[] inputs) {
        if (!recognizes(level, position) || !(level.getBlockEntity(position) instanceof BlockEntityNatureAltar altar) || !altar.isComplete || !altar.items.getStackInSlot(0).isEmpty()) return null;
        var holder = level.getRecipeManager().byKey(recipeId);
        if (holder.isEmpty() || !(holder.get().value() instanceof AltarRecipe recipe) || recipe.getType() != ModRecipes.ALTAR_TYPE) return null;
        var supplied = new KeyCounter();
        for (var counter : inputs) for (var entry : counter) {
            if (!(entry.getKey() instanceof AEItemKey) || entry.getLongValue() <= 0) return null;
            supplied.add(entry.getKey(), entry.getLongValue());
        }
        if (supplied.size() != 1) return null;
        ItemStack input = ((AEItemKey) supplied.iterator().next().getKey()).toStack();
        if (input.getCount() != 1 || !recipe.input.test(input) || pattern.getOutputs().size() != 1 || !PackagedOutputMatching.matches(pattern, recipe.output, recipe.output.getCount())) return null;
        var selected = level.getRecipeManager().getRecipesFor(ModRecipes.ALTAR_TYPE, null, level).stream()
                .filter(candidate -> candidate.id().equals(recipeId) && candidate.value().input.test(input)).findFirst();
        if (selected.isEmpty()) return null;
        var progress = new CompoundTag();
        progress.put("input", input.save(level.registryAccess()));
        progress.put("output", recipe.output.save(level.registryAccess()));
        return progress;
    }

    @Override
    public boolean advance(PackagedMachineOperation operation) {
        if (!recognizes(operation.level(), operation.position()) || !(operation.level().getBlockEntity(operation.position()) instanceof BlockEntityNatureAltar altar)) return false;
        ItemStack expected = read(operation, "output");
        if (operation.progress().getBoolean("waiting")) {
            ItemStack actual = altar.items.getStackInSlot(0);
            if (!PackagedOutputMatching.matches(operation, expected, actual)) return false;
            altar.items.extractItem(0, actual.getCount(), false);
            operation.returned(AEItemKey.of(actual), actual.getCount());
            operation.complete();
            return true;
        }
        ItemStack input = read(operation, "input");
        AEItemKey key = AEItemKey.of(input);
        if (operation.available(key).compareTo(BigInteger.ONE) < 0) throw new IllegalStateException("Missing Nature's Aura altar input");
        if (!altar.items.insertItem(0, input.copy(), true).isEmpty()) return false;
        altar.items.insertItem(0, input.copy(), false);
        operation.delivered(key, 1);
        operation.progress().putBoolean("waiting", true);
        operation.changed();
        return true;
    }

    private static ItemStack read(PackagedMachineOperation operation, String key) {
        return ItemStack.parse(operation.level().registryAccess(), operation.progress().getCompound(key))
                .orElseThrow(() -> new IllegalArgumentException("Invalid Nature's Aura altar item: " + key));
    }
}
