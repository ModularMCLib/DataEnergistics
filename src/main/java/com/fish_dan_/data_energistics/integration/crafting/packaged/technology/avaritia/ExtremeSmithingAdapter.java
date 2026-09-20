package com.fish_dan_.data_energistics.integration.crafting.packaged.technology.avaritia;

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
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

import com.mojang.authlib.GameProfile;
import committee.nova.mods.avaritia.common.block.extreme.ExtremeSmithingTableBlock;
import committee.nova.mods.avaritia.common.crafting.input.ExtremeSmithingRecipeInput;
import committee.nova.mods.avaritia.common.crafting.recipe.ExtremeSmithingRecipe;
import committee.nova.mods.avaritia.common.menu.ExtremeSmithingMenu;
import committee.nova.mods.avaritia.init.registry.ModRecipeTypes;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.jspecify.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Executes Avaritia's five-slot extreme smithing recipe against the native recipe implementation. */
public final class ExtremeSmithingAdapter implements PackagedMachineAdapter {

    private static final ResourceLocation TYPE = ResourceLocation.fromNamespaceAndPath("avaritia", "extreme_smithing");

    @Override
    public ResourceLocation id() {
        return Data_Energistics.id("avaritia_extreme_smithing");
    }

    @Override
    public ObjectSet<ResourceLocation> recipeTypes() {
        return ObjectSet.of(TYPE, ResourceLocation.fromNamespaceAndPath("avaritia", "extreme_smithing_recipe"),
                ResourceLocation.fromNamespaceAndPath("avaritia", "extreme_smithing_table"));
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
        var requirements = new ObjectArrayList<Ingredient>();
        requirements.add(recipe.template);
        requirements.add(recipe.base);
        requirements.add(recipe.additions);
        requirements.add(recipe.additions);
        requirements.add(recipe.additions);
        var assigned = PackagedIngredientAssignment.match(requirements, inputs);
        if (assigned == null || assigned.size() != 5) return null;
        var input = new ExtremeSmithingRecipeInput(assigned.get(0), assigned.get(1), assigned.get(2), assigned.get(3), assigned.get(4));
        if (!recipe.matches(input, level)) return null;
        var selected = level.getRecipeManager().getRecipeFor(ModRecipeTypes.EXTREME_SMITHING_RECIPE.get(), input, level);
        if (selected.isEmpty() || !selected.get().id().equals(recipeId)) return null;
        var result = recipe.assemble(input, level.registryAccess());
        var expected = new ObjectArrayList<ItemStack>();
        expected.add(result);
        for (var stack : assigned) {
            if (stack.getCount() <= 1) continue;
            ItemStack leftover = stack.copy();
            leftover.shrink(1);
            if (!leftover.isEmpty()) expected.add(leftover);
        }
        if (result.isEmpty() || !PackagedIngredientAssignment.outputsMatch(pattern, expected)) return null;
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
        if (!recipe.matches(input, operation.level()) || !PackagedOutputMatching.matches(operation, result, recipe.assemble(input, operation.level().registryAccess()))) {
            throw new IllegalStateException("Avaritia smithing recipe changed after admission");
        }
        var selected = operation.level().getRecipeManager().getRecipeFor(
                ModRecipeTypes.EXTREME_SMITHING_RECIPE.get(), input, operation.level());
        if (selected.isEmpty() || !selected.get().id().equals(operation.recipeId())) {
            throw new IllegalStateException("Avaritia smithing menu selected a different recipe");
        }
        var fake = FakePlayerFactory.get(operation.level(), new GameProfile(
                UUID.nameUUIDFromBytes(operation.id().toString().getBytes(StandardCharsets.UTF_8)),
                "data_energistics_packaged"));
        var menu = new ExtremeSmithingMenu(
                0, fake.getInventory(), ContainerLevelAccess.create(operation.level(), operation.position()));
        for (int index = 0; index < inputs.size(); index++) menu.getSlot(index).set(inputs.get(index).copy());
        menu.createResult();
        var output = menu.getSlot(5);
        ItemStack actual = output.getItem().copy();
        if (!PackagedOutputMatching.matches(operation, result, actual)) throw new IllegalStateException("Unexpected Avaritia smithing output");
        ItemStack taken = output.remove(actual.getCount());
        if (!ItemStack.matches(actual, taken)) throw new IllegalStateException("Avaritia smithing extraction changed");
        output.onTake(fake, taken);
        actual = taken;
        var leftovers = new ObjectArrayList<ItemStack>();
        for (int index = 0; index < inputs.size(); index++) {
            ItemStack leftover = menu.getSlot(index).getItem().copy();
            if (!leftover.isEmpty()) leftovers.add(leftover);
        }
        if (!progress.getBoolean("delivered")) {
            for (var stack : inputs) if (!stack.isEmpty()) operation.delivered(AEItemKey.of(stack), stack.getCount());
            progress.putBoolean("delivered", true);
            operation.changed();
        }
        for (ItemStack leftover : leftovers) operation.returned(AEItemKey.of(leftover), leftover.getCount());
        operation.returned(AEItemKey.of(actual), actual.getCount());
        operation.complete();
        return true;
    }
}
