package com.fish_dan_.data_energistics.integration.crafting.packaged.avaritia;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.crafting.packaged.PackagedMachineAdapter;
import com.fish_dan_.data_energistics.api.crafting.packaged.PackagedMachineOperation;
import com.fish_dan_.data_energistics.common.crafting.packaged.recipe.PackagedIngredientAssignment;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import committee.nova.mods.avaritia.common.block.extreme.ExtremeSmithingTableBlock;
import committee.nova.mods.avaritia.common.crafting.input.ExtremeSmithingRecipeInput;
import committee.nova.mods.avaritia.common.crafting.recipe.ExtremeSmithingRecipe;
import committee.nova.mods.avaritia.init.registry.ModRecipeTypes;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/** Executes Avaritia's five-slot extreme smithing recipe against the native recipe implementation. */
final class ExtremeSmithingAdapter implements PackagedMachineAdapter {

    private static final ResourceLocation TYPE = ResourceLocation.fromNamespaceAndPath("avaritia", "extreme_smithing");

    @Override
    public ResourceLocation id() {
        return Data_Energistics.id("avaritia_extreme_smithing");
    }

    @Override
    public ObjectSet<ResourceLocation> recipeTypes() {
        return ObjectSet.of(TYPE);
    }

    @Override
    public boolean recognizes(ServerLevel level, BlockPos position) {
        return level.isLoaded(position) && level.getBlockState(position).getBlock() instanceof ExtremeSmithingTableBlock;
    }

    @Override
    public @Nullable CompoundTag prepare(ServerLevel level, BlockPos position, Direction face,
                                         ResourceLocation recipeId, IPatternDetails pattern, KeyCounter[] inputs) {
        if (!recognizes(level, position)) return null;
        var holder = level.getRecipeManager().byKey(recipeId);
        if (holder.isEmpty() || !(holder.get().value() instanceof ExtremeSmithingRecipe recipe)) return null;
        var assigned = PackagedIngredientAssignment.match(new ObjectArrayList<>(recipe.getIngredients()), inputs);
        if (assigned == null || assigned.size() != 5) return null;
        var input = new ExtremeSmithingRecipeInput(assigned.get(0), assigned.get(1), assigned.get(2), assigned.get(3), assigned.get(4));
        if (!recipe.matches(input, level)) return null;
        var selected = level.getRecipeManager().getRecipeFor(ModRecipeTypes.EXTREME_SMITHING_RECIPE.get(), input, level);
        if (selected.isEmpty() || !selected.get().id().equals(recipeId)) return null;
        var result = recipe.assemble(input, level.registryAccess());
        if (result.isEmpty() || !PackagedIngredientAssignment.outputsMatch(pattern, ObjectArrayList.of(result))) return null;
        var progress = new CompoundTag();
        var encoded = new ListTag();
        for (var stack : assigned) encoded.add(stack.saveOptional(level.registryAccess()));
        progress.put("inputs", encoded);
        progress.put("result", result.saveOptional(level.registryAccess()));
        return progress;
    }

    @Override
    public boolean advance(PackagedMachineOperation operation) {
        if (!recognizes(operation.level(), operation.position())) return false;
        var progress = operation.progress();
        var encoded = progress.getList("inputs", Tag.TAG_COMPOUND);
        if (encoded.size() != 5) throw new IllegalArgumentException("Invalid persisted Avaritia smithing inputs");
        var inputs = new ObjectArrayList<ItemStack>(5);
        for (int index = 0; index < encoded.size(); index++) {
            inputs.add(ItemStack.parseOptional(operation.level().registryAccess(), encoded.getCompound(index)));
        }
        var result = ItemStack.parse(operation.level().registryAccess(), progress.getCompound("result"))
                .orElseThrow(() -> new IllegalArgumentException("Missing Avaritia smithing result"));
        var holder = operation.level().getRecipeManager().byKey(operation.recipeId());
        if (holder.isEmpty() || !(holder.get().value() instanceof ExtremeSmithingRecipe recipe)) {
            throw new IllegalStateException("Avaritia smithing recipe disappeared after admission");
        }
        var input = new ExtremeSmithingRecipeInput(inputs.get(0), inputs.get(1), inputs.get(2), inputs.get(3), inputs.get(4));
        if (!recipe.matches(input, operation.level()) || !ItemStack.matches(result, recipe.assemble(input, operation.level().registryAccess()))) {
            throw new IllegalStateException("Avaritia smithing recipe changed after admission");
        }
        if (!progress.getBoolean("delivered")) {
            for (var stack : inputs) if (!stack.isEmpty()) operation.delivered(AEItemKey.of(stack), stack.getCount());
            progress.putBoolean("delivered", true);
            operation.changed();
        }
        operation.returned(AEItemKey.of(result), result.getCount());
        operation.complete();
        return true;
    }
}
