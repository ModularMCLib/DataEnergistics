package com.fish_dan_.data_energistics.integration.technology.extendedcrafting.packaged;

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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;

import com.blakebr0.extendedcrafting.api.crafting.ICompressorRecipe;
import com.blakebr0.extendedcrafting.init.ModRecipeTypes;
import com.blakebr0.extendedcrafting.tileentity.CompressorTileEntity;
import it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
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

    @Override
    public ResourceLocation id() {
        return Data_Energistics.id("extended_crafting_quantum_compression");
    }

    @Override
    public ObjectSet<ResourceLocation> recipeTypes() {
        return ObjectSet.of(ResourceLocation.fromNamespaceAndPath("extendedcrafting", "compressor"));
    }

    @Override
    public ObjectSet<ResourceLocation> workstationItemIds() {
        return ObjectSet.of(ResourceLocation.fromNamespaceAndPath("extendedcrafting", "compressor"));
    }

    @Override
    public boolean recognizes(ServerLevel level, BlockPos position) {
        return machine(level, position) != null;
    }

    @Override
    public @Nullable ItemStack completeEncoding(ServerLevel level, ResourceLocation recipeId, ItemStack encodedPattern) {
        var holder = level.getRecipeManager().byKey(recipeId);
        var processing = encodedPattern.get(AEComponents.ENCODED_PROCESSING_PATTERN);
        if (holder.isEmpty() || !(holder.get().value() instanceof ICompressorRecipe recipe) || processing == null)
            return null;
        var counters = new KeyCounter();
        for (var input : processing.sparseInputs()) if (input != null) counters.add(input.what(), input.amount());
        var plan = assign(level, recipe, new KeyCounter[] { counters });
        if (plan == null) {
            // Cucumber's counted ingredient exposes one-item examples; JEI puts its count in a tooltip.
            var viewerInputs = processing.sparseInputs().stream().filter(input -> input != null).toList();
            if (!viewerInputs.isEmpty() && viewerInputs.size() <= 2 &&
                    viewerInputs.stream().allMatch(input -> input.what() instanceof AEItemKey && input.amount() == 1)) {
                ItemStack material = ((AEItemKey) viewerInputs.getFirst().what()).toStack();
                ItemStack[] catalysts = viewerInputs.size() == 2 ?
                        new ItemStack[] { ((AEItemKey) viewerInputs.get(1).what()).toStack() } : recipe.getCatalyst().getItems();
                for (ItemStack catalyst : catalysts) {
                    if (catalyst.isEmpty() || !recipe.matches(CraftingInput.of(2, 1, List.of(material, catalyst)), level))
                        continue;
                    var augmented = new KeyCounter();
                    augmented.add(AEItemKey.of(material), recipe.getCount(0));
                    augmented.add(AEItemKey.of(catalyst), 1);
                    plan = assign(level, recipe, new KeyCounter[] { augmented });
                    if (plan != null) {
                        counters = augmented;
                        break;
                    }
                }
            }
        }
        if (plan == null) {
            for (ItemStack candidate : recipe.getCatalyst().getItems()) {
                if (candidate.isEmpty()) continue;
                var augmented = new KeyCounter();
                for (var entry : counters) augmented.add(entry.getKey(), entry.getLongValue());
                augmented.add(AEItemKey.of(candidate), 1);
                plan = assign(level, recipe, new KeyCounter[] { augmented });
                if (plan != null) {
                    counters = augmented;
                    break;
                }
            }
        }
        if (plan == null) return null;
        var inputs = new ObjectArrayList<GenericStack>();
        for (var entry : counters) inputs.add(new GenericStack(entry.getKey(), entry.getLongValue()));
        ItemStack catalyst = ItemStack.parse(level.registryAccess(), plan.getCompound("catalyst")).orElseThrow();
        ItemStack result = recipe.assemble(CraftingInput.of(2, 1, List.of(
                ItemStack.parse(level.registryAccess(), plan.getList("inputs", Tag.TAG_COMPOUND).getCompound(0).getCompound("item")).orElseThrow(), catalyst)), level.registryAccess());
        ItemStack copy = encodedPattern.copy();
        copy.set(AEComponents.ENCODED_PROCESSING_PATTERN, new EncodedProcessingPattern(inputs,
                List.of(new GenericStack(AEItemKey.of(result), result.getCount()), new GenericStack(AEItemKey.of(catalyst), 1))));
        return copy;
    }

    @Override
    public @Nullable CompoundTag prepare(ServerLevel level, BlockPos position, Direction face,
                                         ResourceLocation recipeId, IPatternDetails pattern, KeyCounter[] inputs) {
        var tile = machine(level, position);
        var holder = level.getRecipeManager().byKey(recipeId);
        if (tile == null || !empty(tile) || holder.isEmpty() || !(holder.get().value() instanceof ICompressorRecipe recipe))
            return null;
        var progress = assign(level, recipe, inputs);
        if (progress == null) return null;
        ItemStack catalyst = ItemStack.parse(level.registryAccess(), progress.getCompound("catalyst")).orElseThrow();
        ItemStack material = ItemStack.parse(level.registryAccess(), progress.getList("inputs", Tag.TAG_COMPOUND)
                .getCompound(0).getCompound("item")).orElseThrow();
        var nativeInput = CraftingInput.of(2, 1, List.of(material, catalyst));
        var selected = level.getRecipeManager().getRecipeFor(ModRecipeTypes.COMPRESSOR.get(), nativeInput, level);
        if (selected.isEmpty() || !selected.get().id().equals(recipeId)) return null;
        ItemStack result = recipe.assemble(nativeInput, level.registryAccess());
        if (result.isEmpty() || !PackagedIngredientAssignment.outputsMatch(pattern, ObjectArrayList.of(result, catalyst)))
            return null;
        progress.put("result", result.save(level.registryAccess()));
        progress.putInt("required", recipe.getCount(0));
        return progress;
    }

    private static @Nullable CompoundTag assign(ServerLevel level, ICompressorRecipe recipe, KeyCounter[] inputs) {
        int required = recipe.getCount(0);
        if (required <= 0) return null;
        var counts = new Object2LongLinkedOpenHashMap<AEItemKey>();
        long total = 0;
        for (var counter : inputs)
            for (var entry : counter) {
                if (!(entry.getKey() instanceof AEItemKey item) || entry.getLongValue() <= 0 || entry.getLongValue() > (long) required + 1)
                    return null;
                counts.addTo(item, entry.getLongValue());
                total += entry.getLongValue();
                if (total > (long) required + 1) return null;
            }
        if (total != (long) required + 1) return null;
        for (var candidate : counts.keySet()) {
            ItemStack catalyst = candidate.toStack();
            if (!recipe.getCatalyst().test(catalyst)) continue;
            var encoded = new ListTag();
            boolean matches = true;
            for (var entry : counts.object2LongEntrySet()) {
                long amount = entry.getLongValue() - (entry.getKey().equals(candidate) ? 1 : 0);
                if (amount == 0) continue;
                ItemStack material = entry.getKey().toStack();
                if (!recipe.matches(CraftingInput.of(2, 1, List.of(material, catalyst)), level)) {
                    matches = false;
                    break;
                }
                var portion = new CompoundTag();
                portion.put("item", material.save(level.registryAccess()));
                portion.putLong("count", amount);
                encoded.add(portion);
            }
            // Native canInsertItem applies IngredientWithCount to a new material identity. Once accumulation
            // starts, a different variant smaller than the entire recipe cost would never be consumed.
            if (!matches || encoded.size() != 1) continue;
            var progress = new CompoundTag();
            progress.put("inputs", encoded);
            progress.put("catalyst", catalyst.save(level.registryAccess()));
            return progress;
        }
        return null;
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
