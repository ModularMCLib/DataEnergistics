package com.fish_dan_.data_energistics.integration.technology.extendedcrafting.packaged;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.crafting.packaged.PackagedMachineAdapter;
import com.fish_dan_.data_energistics.api.crafting.packaged.PackagedMachineOperation;
import com.fish_dan_.data_energistics.common.crafting.packaged.recipe.PackagedOutputMatching;

import appeng.api.crafting.IPatternDetails;
import appeng.api.ids.AEComponents;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.pattern.EncodedProcessingPattern;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

import com.blakebr0.extendedcrafting.api.crafting.ICompressorRecipe;
import com.blakebr0.extendedcrafting.init.ModRecipeTypes;
import com.blakebr0.extendedcrafting.tileentity.CompressorTileEntity;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.math.BigInteger;
import java.util.List;

/**
 * Native quantum compression with a borrowed catalyst and bounded inventory transfers.
 */
@NullMarked
public final class QuantumCompressionAdapter implements PackagedMachineAdapter {

    static final ResourceLocation RECIPE_TYPE = Data_Energistics.id("extendedcrafting:compressor");

    @Override
    public ResourceLocation id() {
        return Data_Energistics.id("extended_crafting_quantum_compression");
    }

    @Override
    public ObjectSet<ResourceLocation> recipeTypes() {
        return ObjectSet.of(RECIPE_TYPE);
    }

    @Override
    public ObjectSet<ResourceLocation> workstationItemIds() {
        return ObjectSet.of(RECIPE_TYPE);
    }

    @Override
    public boolean recognizes(ServerLevel level, BlockPos position) {
        return machine(level, position) != null;
    }

    @Override
    public boolean supportsReusableInputs() {
        return true;
    }

    @Override
    public @Nullable ItemStack completeEncoding(ServerLevel level, ResourceLocation recipeId, ItemStack encodedPattern) {
        var holder = level.getRecipeManager().byKey(recipeId);
        var processing = encodedPattern.get(AEComponents.ENCODED_PROCESSING_PATTERN);
        if (holder.isEmpty() || !(holder.get().value() instanceof ICompressorRecipe recipe) || processing == null)
            return null;
        var completion = QuantumCompressionInputs.completeEncoding(level, recipe, processing.sparseInputs());
        if (completion == null) return null;
        var plan = completion.plan();
        ItemStack result = recipe.assemble(plan.craftingInput(), level.registryAccess());
        if (result.isEmpty()) return null;
        var primary = new GenericStack(AEItemKey.of(result), result.getCount());
        var outputs = plan.hasSeparateCatalyst(level, recipe) ?
                List.of(primary) : List.of(primary, new GenericStack(plan.catalyst(), 1));
        ItemStack copy = encodedPattern.copy();
        copy.set(AEComponents.ENCODED_PROCESSING_PATTERN, new EncodedProcessingPattern(completion.sparseInputs(), outputs));
        return copy;
    }

    @Override
    public @Nullable CompoundTag prepare(ServerLevel level, BlockPos position, Direction face,
                                         ResourceLocation recipeId, IPatternDetails pattern, KeyCounter[] inputs) {
        var tile = machine(level, position);
        var holder = level.getRecipeManager().byKey(recipeId);
        if (tile == null || !empty(tile) || holder.isEmpty() || !(holder.get().value() instanceof ICompressorRecipe recipe))
            return null;
        var plan = QuantumCompressionInputs.match(level, recipe, inputs);
        if (plan == null) return null;
        var prepared = prepare(level, recipeId, pattern, plan);
        // For a one-item recipe accepting either assignment, the declared return identifies the catalyst.
        if (prepared == null && plan.required() == 1 && !plan.material().equals(plan.catalyst())) {
            return prepare(level, recipeId, pattern, plan.reversed());
        }
        return prepared;
    }

    private static @Nullable CompoundTag prepare(ServerLevel level, ResourceLocation recipeId, IPatternDetails pattern,
                                                 QuantumCompressionInputs plan) {
        var input = plan.craftingInput();
        var selected = level.getRecipeManager().getRecipeFor(ModRecipeTypes.COMPRESSOR.get(), input, level);
        if (selected.isEmpty() || !selected.get().id().equals(recipeId)) return null;
        ItemStack result = selected.get().value().assemble(input, level.registryAccess());
        if (result.isEmpty()) return null;
        ItemStack catalyst = plan.catalyst().toStack();
        if (!PackagedOutputMatching.matches(pattern, result, result.getCount()) &&
                !PackagedOutputMatching.matches(pattern, ObjectList.of(result, catalyst)))
            return null;

        var portion = new CompoundTag();
        portion.put("item", plan.material().toStack().save(level.registryAccess()));
        portion.putLong("count", plan.required());
        var encoded = new ListTag();
        encoded.add(portion);
        var progress = new CompoundTag();
        progress.put("inputs", encoded);
        progress.put("catalyst", catalyst.save(level.registryAccess()));
        progress.put("result", result.save(level.registryAccess()));
        progress.putInt("required", plan.required());
        return progress;
    }

    @Override
    public boolean advance(PackagedMachineOperation operation) {
        var tile = machine(operation.level(), operation.position());
        if (tile == null) return false;
        var progress = operation.progress();
        var inventory = tile.getInventory();
        ItemStack catalyst = ItemStack.parse(operation.level().registryAccess(), progress.getCompound("catalyst")).orElseThrow();
        if (!progress.getBoolean("catalystDelivered")) {
            if (!empty(tile)) return false;
            if (operation.available(AEItemKey.of(catalyst)).signum() <= 0)
                throw new IllegalStateException("Quantum compressor catalyst is no longer owned by this operation");
            // The native handler rejects automation in slot 2; this is the same slot write as the native menu.
            inventory.setStackInSlot(2, catalyst.copy());
            operation.delivered(AEItemKey.of(catalyst), 1);
            progress.putBoolean("catalystDelivered", true);
            operation.changed();
        }
        if (!ItemStack.matches(catalyst, inventory.getStackInSlot(2)))
            throw new IllegalStateException("Extended Crafting compressor catalyst changed");
        if (tile.isEjecting()) throw new IllegalStateException("Extended Crafting compressor switched to eject mode");
        ItemStack result = ItemStack.parse(operation.level().registryAccess(), progress.getCompound("result")).orElseThrow();
        ItemStack output = inventory.getStackInSlot(0).copy();
        if (!output.isEmpty()) {
            if (progress.getLong("delivered") != progress.getInt("required") || tile.getMaterialCount() != 0 ||
                    !inventory.getStackInSlot(1).isEmpty() || !PackagedOutputMatching.matches(operation, result, output))
                throw new IllegalStateException("Unexpected Extended Crafting compression result or remaining materials");
            ItemStack extracted = inventory.extractItem(0, output.getCount(), false);
            if (!ItemStack.matches(extracted, output))
                throw new IllegalStateException("Compressor output extraction changed");
            inventory.setStackInSlot(2, ItemStack.EMPTY);
            operation.returned(AEItemKey.of(extracted), extracted.getCount());
            operation.returned(AEItemKey.of(catalyst), 1);
            operation.complete();
            return true;
        }
        var encoded = progress.getList("inputs", Tag.TAG_COMPOUND);
        int index = progress.getInt("index");
        if (index >= encoded.size()) return false;
        var portion = encoded.getCompound(index);
        ItemStack input = ItemStack.parse(operation.level().registryAccess(), portion.getCompound("item")).orElseThrow();
        long remaining = portion.getLong("count");
        int amount = (int) Math.min(remaining, input.getMaxStackSize());
        if (amount <= 0) throw new IllegalArgumentException("Invalid quantum compressor input count");
        if (operation.available(AEItemKey.of(input)).compareTo(BigInteger.valueOf(amount)) < 0)
            throw new IllegalStateException("Quantum compressor plan exceeds remaining owned materials");
        int accepted = amount - inventory.insertItem(1, input.copyWithCount(amount), false).getCount();
        if (accepted == 0) return false;
        operation.delivered(AEItemKey.of(input), accepted);
        portion.putLong("count", remaining - accepted);
        progress.putLong("delivered", progress.getLong("delivered") + accepted);
        if (remaining == accepted) progress.putInt("index", index + 1);
        operation.changed();
        return true;
    }

    private static boolean empty(CompressorTileEntity tile) {
        if (tile.getMaterialCount() != 0 || tile.getProgress() != 0 || tile.isEjecting()) return false;
        for (int slot = 0; slot < tile.getInventory().getSlots(); slot++)
            if (!tile.getInventory().getStackInSlot(slot).isEmpty()) return false;
        return true;
    }

    private static @Nullable CompressorTileEntity machine(ServerLevel level, BlockPos position) {
        return level.isLoaded(position) && level.getBlockEntity(position) instanceof CompressorTileEntity tile ? tile : null;
    }
}
