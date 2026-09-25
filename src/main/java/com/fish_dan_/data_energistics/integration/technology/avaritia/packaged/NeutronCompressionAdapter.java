package com.fish_dan_.data_energistics.integration.technology.avaritia.packaged;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.crafting.packaged.PackagedMachineAdapter;
import com.fish_dan_.data_energistics.api.crafting.packaged.PackagedMachineOperation;
import com.fish_dan_.data_energistics.common.crafting.packaged.recipe.PackagedIngredientAssignment;
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
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;

import committee.nova.mods.avaritia.api.common.crafting.ICompressorRecipe;
import committee.nova.mods.avaritia.common.tile.NeutronCompressorTile;
import committee.nova.mods.avaritia.init.registry.ModRecipeTypes;
import committee.nova.mods.avaritia.init.registry.enums.CompressorTier;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.math.BigInteger;
import java.util.List;

/**
 * Feeds the native compressor's accumulator in slot-sized portions, retaining tier costs and timers.
 */
@NullMarked
public final class NeutronCompressionAdapter implements PackagedMachineAdapter {

    @Override
    public ResourceLocation id() {
        return Data_Energistics.id("avaritia_neutron_compression");
    }

    @Override
    public ObjectSet<ResourceLocation> recipeTypes() {
        return ObjectSet.of(ResourceLocation.fromNamespaceAndPath("avaritia", "compressor_recipe"),
                ResourceLocation.fromNamespaceAndPath("avaritia", "compressor"));
    }

    @Override
    public ObjectSet<ResourceLocation> workstationItemIds() {
        return ObjectSet.of(
                ResourceLocation.fromNamespaceAndPath("avaritia", "neutron_compressor"),
                ResourceLocation.fromNamespaceAndPath("avaritia", "dense_neutron_compressor"),
                ResourceLocation.fromNamespaceAndPath("avaritia", "denser_neutron_compressor"),
                ResourceLocation.fromNamespaceAndPath("avaritia", "densest_neutron_compressor"));
    }

    @Override
    public boolean recognizes(ServerLevel level, BlockPos position) {
        return machine(level, position) != null;
    }

    @Override
    public @Nullable ItemStack completeEncoding(ServerLevel level, ResourceLocation recipeId, ItemStack encodedPattern) {
        var holder = level.getRecipeManager().byKey(recipeId);
        var processing = encodedPattern.get(AEComponents.ENCODED_PROCESSING_PATTERN);
        if (holder.isEmpty() || !(holder.get().value() instanceof ICompressorRecipe recipe) || processing == null ||
                recipe.getInputCount() <= 0)
            return null;
        @Nullable
        GenericStack selected = null;
        for (var input : processing.sparseInputs())
            if (input != null) {
                if (selected != null || !(input.what() instanceof AEItemKey item) ||
                        !recipe.getInput().test(item.toStack()) || input.amount() <= 0)
                    return null;
                selected = input;
            }
        if (selected == null) return null;
        var declaredOutputs = new KeyCounter();
        for (var output : processing.sparseOutputs())
            if (output != null) declaredOutputs.add(output.what(), output.amount());
        ItemStack baseResult = recipe.getResultItem(level.registryAccess());
        if (baseResult.isEmpty()) return null;
        for (CompressorTier tier : CompressorTier.values()) {
            if (selected.amount() == Mth.ceil(recipe.getInputCount() * tier.inputAmplifier) &&
                    declaredOutputs.size() == 1 && declaredOutputs.get(AEItemKey.of(baseResult)) ==
                            (long) baseResult.getCount() * tier.outputAmplifier)
                return encodedPattern;
        }
        // JEI renders the ingredient once and puts the required count only in its tooltip.
        if (selected.amount() != 1) return encodedPattern;
        ItemStack copy = encodedPattern.copy();
        copy.set(AEComponents.ENCODED_PROCESSING_PATTERN, new EncodedProcessingPattern(
                List.of(new GenericStack(selected.what(), recipe.getInputCount())), processing.sparseOutputs()));
        return copy;
    }

    @Override
    public @Nullable CompoundTag prepare(ServerLevel level, BlockPos position, Direction face,
                                         ResourceLocation recipeId, IPatternDetails pattern, KeyCounter[] inputs) {
        var tile = machine(level, position);
        var holder = level.getRecipeManager().byKey(recipeId);
        if (tile == null || !empty(tile) || holder.isEmpty() ||
                !(holder.get().value() instanceof ICompressorRecipe recipe))
            return null;
        if (tile.isRecipeLocked() && tile.getLockedRecipe() != null && tile.getLockedRecipe() != recipe) return null;
        int required = Mth.ceil(recipe.getInputCount() * tile.getTier().inputAmplifier);
        if (required <= 0) return null;
        var encoded = new ListTag();
        long total = 0;
        ItemStack representative = ItemStack.EMPTY;
        for (var counter : inputs)
            for (var entry : counter) {
                if (!(entry.getKey() instanceof AEItemKey item) || entry.getLongValue() <= 0 ||
                        entry.getLongValue() > required || !recipe.getInput().test(item.toStack()))
                    return null;
                total += entry.getLongValue();
                if (total > required) return null;
                representative = item.toStack();
                var portion = new CompoundTag();
                portion.put("item", representative.save(level.registryAccess()));
                portion.putLong("count", entry.getLongValue());
                encoded.add(portion);
            }
        if (total != required) return null;
        var nativeInput = CraftingInput.of(1, 1, List.of(representative));
        var selected = level.getRecipeManager().getRecipeFor(ModRecipeTypes.COMPRESSOR_RECIPE.get(), nativeInput, level);
        if (selected.isEmpty() || !selected.get().id().equals(recipeId)) return null;
        ItemStack base = recipe.assemble(nativeInput, level.registryAccess());
        ItemStack result = base.copyWithCount(Math.multiplyExact(base.getCount(), tile.getTier().outputAmplifier));
        if (result.isEmpty() || result.getCount() > result.getMaxStackSize() ||
                !PackagedIngredientAssignment.outputsMatch(pattern, ObjectArrayList.of(result)))
            return null;
        var progress = new CompoundTag();
        progress.put("inputs", encoded);
        progress.put("result", result.save(level.registryAccess()));
        progress.putString("tier", tile.getTier().name());
        progress.putInt("required", required);
        return progress;
    }

    @Override
    public boolean advance(PackagedMachineOperation operation) {
        var tile = machine(operation.level(), operation.position());
        if (tile == null) return false;
        var progress = operation.progress();
        if (!tile.getTier().name().equals(progress.getString("tier")))
            throw new IllegalStateException("Avaritia compressor tier changed during operation");
        var inventory = tile.getInventory();
        var result = ItemStack.parse(operation.level().registryAccess(), progress.getCompound("result")).orElseThrow();
        var output = inventory.getStackInSlot(0).copy();
        if (!output.isEmpty()) {
            if (progress.getLong("delivered") != progress.getInt("required") || tile.getMaterialCount() != 0 ||
                    !inventory.getStackInSlot(1).isEmpty() || !PackagedOutputMatching.matches(operation, result, output))
                throw new IllegalStateException("Unexpected Avaritia compressor output or remaining input");
            ItemStack extracted = inventory.extractItem(0, output.getCount(), false);
            if (!ItemStack.matches(extracted, output))
                throw new IllegalStateException("Avaritia compressor output extraction changed");
            operation.returned(AEItemKey.of(extracted), extracted.getCount());
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
        if (amount <= 0) throw new IllegalArgumentException("Invalid Avaritia compressor input count");
        if (operation.available(AEItemKey.of(input)).compareTo(BigInteger.valueOf(amount)) < 0)
            throw new IllegalStateException("Avaritia compressor plan exceeds remaining owned materials");
        ItemStack offered = input.copyWithCount(amount);
        var rejected = inventory.insertItem(1, offered, false);
        int accepted = amount - rejected.getCount();
        if (accepted == 0) return false;
        operation.delivered(AEItemKey.of(input), accepted);
        portion.putLong("count", remaining - accepted);
        progress.putLong("delivered", progress.getLong("delivered") + accepted);
        if (remaining == accepted) progress.putInt("index", index + 1);
        operation.changed();
        return true;
    }

    private static boolean empty(NeutronCompressorTile tile) {
        return tile.getMaterialCount() == 0 && tile.getInventory().getStackInSlot(0).isEmpty() &&
                tile.getInventory().getStackInSlot(1).isEmpty();
    }

    private static @Nullable NeutronCompressorTile machine(ServerLevel level, BlockPos position) {
        return level.isLoaded(position) && level.getBlockEntity(position) instanceof NeutronCompressorTile tile ? tile : null;
    }
}
