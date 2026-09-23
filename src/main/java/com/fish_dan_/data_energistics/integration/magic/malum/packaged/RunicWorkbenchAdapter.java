package com.fish_dan_.data_energistics.integration.magic.malum.packaged;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.crafting.packaged.PackagedMachineAdapter;
import com.fish_dan_.data_energistics.api.crafting.packaged.PackagedMachineOperation;
import com.fish_dan_.data_energistics.common.crafting.packaged.execution.PackagedEntityCapture;
import com.fish_dan_.data_energistics.common.crafting.packaged.recipe.PackagedOutputMatching;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.phys.AABB;

import com.sammy.malum.common.block.curiosities.runic_workbench.RunicWorkbenchBlockEntity;
import com.sammy.malum.common.recipe.RuneworkingRecipe;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.jspecify.annotations.Nullable;

import java.math.BigInteger;

/** Uses the native runic-workbench right-click path, including its twenty-tick craft delay. */
public final class RunicWorkbenchAdapter implements PackagedMachineAdapter {

    private static final ResourceLocation RECIPE_TYPE = ResourceLocation.fromNamespaceAndPath("malum", "runeworking");

    @Override
    public ResourceLocation id() {
        return Data_Energistics.id("malum_runeworking");
    }

    @Override
    public ObjectSet<ResourceLocation> recipeTypes() {
        return ObjectSet.of(RECIPE_TYPE);
    }

    @Override
    public ObjectSet<ResourceLocation> workstationItemIds() {
        return ObjectSet.of(ResourceLocation.fromNamespaceAndPath("malum", "runic_workbench"));
    }

    @Override
    public boolean recognizes(ServerLevel level, BlockPos position) {
        return level.isLoaded(position) && level.getBlockEntity(position) instanceof RunicWorkbenchBlockEntity;
    }

    @Override
    public @Nullable CompoundTag prepare(ServerLevel level, BlockPos position, Direction face,
                                         ResourceLocation recipeId, IPatternDetails pattern, KeyCounter[] inputs) {
        if (!recognizes(level, position)) return null;
        var holder = level.getRecipeManager().byKey(recipeId);
        if (holder.isEmpty() || !(holder.get().value() instanceof RuneworkingRecipe recipe)) return null;
        var assigned = assign(recipe, inputs);
        if (assigned == null) return null;
        if (!matchesOutput(pattern, recipe.output)) return null;
        var result = new CompoundTag();
        result.put("primary", assigned.primary().save(level.registryAccess()));
        result.put("secondary", assigned.secondary().save(level.registryAccess()));
        result.put("output", recipe.output.save(level.registryAccess()));
        return result;
    }

    @Override
    public boolean advance(PackagedMachineOperation operation) {
        if (!(operation.level().getBlockEntity(operation.position()) instanceof RunicWorkbenchBlockEntity workbench)) return false;
        var progress = operation.progress();
        var output = read(operation, progress.getCompound("output"));
        if (progress.getBoolean("delivered")) {
            var drops = operation.level().getEntitiesOfClass(ItemEntity.class, new AABB(operation.position()).inflate(3),
                    entity -> PackagedEntityCapture.ownedBy(entity, operation.id()));
            long count = 0;
            for (var drop : drops) if (PackagedOutputMatching.sameKey(operation, output, drop.getItem())) count += drop.getItem().getCount();
            if (count < output.getCount()) return false;
            for (var drop : drops) {
                var stack = drop.getItem().copy();
                drop.discard();
                operation.returned(AEItemKey.of(stack), stack.getCount());
            }
            operation.complete();
            return true;
        }
        if (!workbench.getSuppliedInventory().isEmpty()) return false;
        var primary = read(operation, progress.getCompound("primary"));
        var secondary = read(operation, progress.getCompound("secondary"));
        var required = new KeyCounter();
        required.add(AEItemKey.of(primary), primary.getCount());
        required.add(AEItemKey.of(secondary), secondary.getCount());
        for (var entry : required) if (operation.available(entry.getKey()).compareTo(BigInteger.valueOf(entry.getLongValue())) < 0)
            throw new IllegalStateException("Missing owned Malum runeworking input");
        boolean accepted = workbench.tryCraft(operation.level(), primary.copy(), secondary.copy(), true);
        if (!accepted) return false;
        operation.delivered(AEItemKey.of(primary), primary.getCount());
        operation.delivered(AEItemKey.of(secondary), secondary.getCount());
        progress.putBoolean("delivered", true);
        operation.changed();
        return true;
    }

    private static @Nullable Assigned assign(RuneworkingRecipe recipe, KeyCounter[] supplied) {
        var primary = recipe.input.ingredient();
        var secondary = recipe.secondaryInput.ingredient();
        var counts = new KeyCounter();
        for (var counter : supplied) for (var entry : counter) {
            if (!(entry.getKey() instanceof AEItemKey) || entry.getLongValue() <= 0) return null;
            counts.add(entry.getKey(), entry.getLongValue());
        }
        var p = find(counts, primary, recipe.input.count());
        if (p.isEmpty()) return null;
        counts.add(AEItemKey.of(p), -recipe.input.count());
        var s = find(counts, secondary, recipe.secondaryInput.count());
        if (s.isEmpty()) return null;
        counts.add(AEItemKey.of(s), -recipe.secondaryInput.count());
        for (var entry : counts) if (entry.getLongValue() != 0) return null;
        return new Assigned(p, s);
    }

    private static ItemStack find(KeyCounter counts, Ingredient ingredient, int amount) {
        for (var entry : counts) if (entry.getLongValue() >= amount && ingredient.test(((AEItemKey) entry.getKey()).toStack()))
            return ((AEItemKey) entry.getKey()).toStack(amount);
        return ItemStack.EMPTY;
    }

    private static boolean matchesOutput(IPatternDetails pattern, ItemStack output) {
        return PackagedOutputMatching.matches(pattern, output, output.getCount());
    }

    private static ItemStack read(PackagedMachineOperation operation, CompoundTag tag) {
        return ItemStack.parse(operation.level().registryAccess(), tag).orElseThrow(() -> new IllegalArgumentException("Invalid Malum runeworking item"));
    }

    private record Assigned(ItemStack primary, ItemStack secondary) {}
}
