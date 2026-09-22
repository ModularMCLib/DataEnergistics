package com.fish_dan_.data_energistics.integration.magic.neovitae.packaged;

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

import com.breakinblocks.neovitae.api.recipe.AraVitaeInput;
import com.breakinblocks.neovitae.api.recipe.AraVitaeRecipe;
import com.breakinblocks.neovitae.common.blockentity.AraVitaeTile;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.jspecify.annotations.Nullable;

import java.math.BigInteger;

/** Supplies the item side of one Neo Vitae Ara Vitae craft and preserves the native blood/tier checks. */
public final class AraVitaeAdapter implements PackagedMachineAdapter {

    private static final ResourceLocation RECIPE_TYPE = ResourceLocation.fromNamespaceAndPath("neovitae", "ara_vitae");

    @Override
    public ResourceLocation id() {
        return Data_Energistics.id("neovitae_ara_vitae");
    }

    @Override
    public ObjectSet<ResourceLocation> recipeTypes() {
        return ObjectSet.of(RECIPE_TYPE);
    }

    @Override
    public boolean recognizes(ServerLevel level, BlockPos position) {
        return level.isLoaded(position) && level.getBlockEntity(position) instanceof AraVitaeTile;
    }

    @Override
    public @Nullable CompoundTag prepare(ServerLevel level, BlockPos position, Direction face,
                                         ResourceLocation recipeId, IPatternDetails pattern, KeyCounter[] inputs) {
        if (!recognizes(level, position) || !(level.getBlockEntity(position) instanceof AraVitaeTile altar) || !altar.getStackInSlot().isEmpty()) return null;
        var holder = level.getRecipeManager().byKey(recipeId);
        if (holder.isEmpty() || !(holder.get().value() instanceof AraVitaeRecipe recipe) || recipe.getType() != holder.get().value().getType() || altar.getTier() < recipe.getMinTier()) return null;
        var supplied = new KeyCounter();
        for (var counter : inputs) for (var entry : counter) {
            if (!(entry.getKey() instanceof AEItemKey) || entry.getLongValue() <= 0) return null;
            supplied.add(entry.getKey(), entry.getLongValue());
        }
        if (supplied.size() != 1) return null;
        ItemStack input = ((AEItemKey) supplied.iterator().next().getKey()).toStack();
        var recipeInput = new AraVitaeInput(input, altar.getTier());
        if (input.getCount() != 1 || !recipe.matches(recipeInput, level) || pattern.getOutputs().size() != 1 || !PackagedOutputMatching.matches(pattern, recipe.getResult(), recipe.getResult().getCount())) return null;
        var progress = new CompoundTag();
        progress.put("input", input.save(level.registryAccess()));
        progress.put("output", recipe.getResult().save(level.registryAccess()));
        return progress;
    }

    @Override
    public boolean advance(PackagedMachineOperation operation) {
        if (!recognizes(operation.level(), operation.position()) || !(operation.level().getBlockEntity(operation.position()) instanceof AraVitaeTile altar)) return false;
        ItemStack expected = read(operation, "output");
        if (operation.progress().getBoolean("waiting")) {
            ItemStack actual = altar.getStackInSlot();
            if (!PackagedOutputMatching.matches(operation, expected, actual)) return false;
            altar.inv.extractItem(0, actual.getCount(), false);
            operation.returned(AEItemKey.of(actual), actual.getCount());
            operation.complete();
            return true;
        }
        ItemStack input = read(operation, "input");
        AEItemKey key = AEItemKey.of(input);
        if (operation.available(key).compareTo(BigInteger.ONE) < 0) throw new IllegalStateException("Missing Neo Vitae altar input");
        if (!altar.inv.insertItem(0, input.copy(), true).isEmpty()) return false;
        altar.inv.insertItem(0, input.copy(), false);
        operation.delivered(key, 1);
        operation.progress().putBoolean("waiting", true);
        operation.changed();
        return true;
    }

    private static ItemStack read(PackagedMachineOperation operation, String key) {
        return ItemStack.parse(operation.level().registryAccess(), operation.progress().getCompound(key))
                .orElseThrow(() -> new IllegalArgumentException("Invalid Neo Vitae altar item: " + key));
    }
}
