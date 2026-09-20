package com.fish_dan_.data_energistics.integration.patternprovider.ae.ae2cs;

import com.fish_dan_.data_energistics.api.registry.adaptive.AdaptivePatternProviderDispatch;
import com.fish_dan_.data_energistics.api.registry.adaptive.AdaptivePatternProviderDispatchContext;
import com.fish_dan_.data_energistics.api.registry.adaptive.AdaptivePatternProviderDispatchTarget;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;

import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

/**
 * AE2 Crystal Science molecular-assembler route for the meteorite provider.
 *
 * <p>
 * All provider-specific buffering and energy policy lives here; the core
 * only exposes generic reusable-work and inventory operations.
 * </p>
 */
public final class Ae2CrystalScienceMeteoriteRoute implements AdaptivePatternProviderDispatch {

    private static final int ENERGY_PER_WORK = 50;
    private static final double ENERGY_TOLERANCE = 1.0e-9;
    private static final int MAX_WORKS_PER_ROUND = 8;
    private static final String NBT_CRAFTED_CONTENTS = "adaptive_crafted_contents";

    @Override
    public String legacyStateKey() {
        return NBT_CRAFTED_CONTENTS;
    }

    @Override
    public boolean usesSpecialBatchRoute(IPatternDetails patternDetails) {
        return true;
    }

    @Override
    public boolean handles(AdaptivePatternProviderDispatchContext context) {
        return context.patternDetails() instanceof IMolecularAssemblerSupportedPattern;
    }

    @Override
    public boolean supportsReusablePatterns() {
        return true;
    }

    /** Dispatches one molecular-assembler pattern into the buffered output state. */
    @Override
    public boolean dispatch(AdaptivePatternProviderDispatchContext dispatchContext) {
        AdaptivePatternProviderDispatchTarget target = dispatchContext.target();
        IPatternDetails details = dispatchContext.patternDetails();
        KeyCounter[] inputHolder = dispatchContext.inputHolder();
        if (!(details instanceof IMolecularAssemblerSupportedPattern pattern) || target.reusableHandoffPrepared() || target.isBusy() || !target.isActive() || !target.hasPattern(details) || availableAdmissions(target) <= 0 || !target.hasAvailableNativeSlot(details) || !(target.level() instanceof ServerLevel level) || !hasEnergy(target)) {
            return false;
        }

        List<GenericStack> output = patternOutput(pattern, inputHolder, level);
        if (output == null || output.stream().noneMatch(stack -> stack != null && stack.amount() > 0)) {
            return false;
        }
        if (!consumeEnergy(target)) {
            return false;
        }

        State state = target.routeState(State.class, State::new);
        boolean wasEmpty = state.craftedContents.isEmpty();
        for (GenericStack stack : output) {
            if (stack != null && stack.what() != null && stack.amount() > 0) {
                state.craftedContents.addTo(stack.what(), stack.amount());
            }
        }
        target.addReusableWork(1);
        target.saveChanges();
        if (wasEmpty && !state.craftedContents.isEmpty()) {
            target.alertDevice();
        }
        return true;
    }

    /** Returns the reusable operation budget for the current provider upgrades. */
    @Override
    public int reusableWorkLimit(AdaptivePatternProviderDispatchTarget target) {
        return MAX_WORKS_PER_ROUND << Math.min(4, target.installedSpeedCardCount());
    }

    /** Returns the per-operation energy cost for the current provider upgrades. */
    @Override
    public double reusableEnergyPerWork(AdaptivePatternProviderDispatchTarget target) {
        return (double) (ENERGY_PER_WORK << Math.min(4, target.installedSpeedCardCount()));
    }

    /** Returns whether buffered outputs or route work keep the provider awake. */
    @Override
    public boolean hasWork(AdaptivePatternProviderDispatchTarget target) {
        return !target.routeState(State.class, State::new).craftedContents.isEmpty();
    }

    /** Flushes one tick of buffered molecular-assembler outputs into the return inventory. */
    @Override
    public boolean tick(AdaptivePatternProviderDispatchTarget target, int ticksSinceLastCall) {
        State state = target.routeState(State.class, State::new);
        if (state.craftedContents.isEmpty()) {
            return false;
        }

        boolean changed = false;
        var iterator = state.craftedContents.object2LongEntrySet().iterator();
        while (iterator.hasNext()) {
            Object2LongMap.Entry<AEKey> entry = iterator.next();
            AEKey key = entry.getKey();
            long remaining = entry.getLongValue();
            if (key == null || remaining <= 0) {
                iterator.remove();
                changed = true;
                continue;
            }

            long inserted = target.returnInventory().insert(
                    key, remaining, Actionable.MODULATE, target.actionSource());
            if (inserted <= 0) {
                continue;
            }
            remaining -= inserted;
            if (remaining <= 0) {
                iterator.remove();
            } else {
                entry.setValue(remaining);
            }
            changed = true;
        }
        if (changed) {
            target.saveChanges();
        }
        return changed;
    }

    /** Merges outputs produced by a resident reusable session into the route buffer. */
    @Override
    public void acceptReusableOutputsFast(AdaptivePatternProviderDispatchTarget target, ObjectList<GenericStack> outputs) {
        State state = target.routeState(State.class, State::new);
        Object2LongOpenHashMap<AEKey> next = new Object2LongOpenHashMap<>(state.craftedContents);
        for (GenericStack output : outputs) {
            if (output != null && output.what() != null && output.amount() > 0) {
                next.put(output.what(), Math.addExact(next.getLong(output.what()), output.amount()));
            }
        }
        state.craftedContents.clear();
        state.craftedContents.putAll(next);
    }

    /** Writes the route's buffered output state using the legacy compatible keys. */
    @Override
    public void writeState(
                           AdaptivePatternProviderDispatchTarget target,
                           CompoundTag tag,
                           HolderLookup.Provider registries) {
        State state = target.routeState(State.class, State::new);
        ListTag contents = new ListTag();
        for (var entry : state.craftedContents.object2LongEntrySet()) {
            if (entry.getKey() != null && entry.getLongValue() > 0) {
                contents.add(GenericStack.writeTag(
                        registries, new GenericStack(entry.getKey(), entry.getLongValue())));
            }
        }
        tag.put(NBT_CRAFTED_CONTENTS, contents);
    }

    /** Restores the route's buffered output state. */
    @Override
    public void readState(
                          AdaptivePatternProviderDispatchTarget target,
                          CompoundTag tag,
                          HolderLookup.Provider registries) {
        State state = target.routeState(State.class, State::new);
        state.craftedContents.clear();
        ListTag contents = tag.getList(NBT_CRAFTED_CONTENTS, Tag.TAG_COMPOUND);
        for (int index = 0; index < contents.size(); index++) {
            GenericStack stack = GenericStack.readTag(registries, contents.getCompound(index));
            if (stack != null && stack.what() != null && stack.amount() > 0) {
                state.craftedContents.addTo(stack.what(), stack.amount());
            }
        }
    }

    /** Adds buffered outputs to block or part drops. */
    @Override
    public void addDropsFast(AdaptivePatternProviderDispatchTarget target, ObjectList<ItemStack> drops) {
        State state = target.routeState(State.class, State::new);
        for (var entry : state.craftedContents.object2LongEntrySet()) {
            if (entry.getKey() != null && entry.getLongValue() > 0) {
                entry.getKey().addDrops(
                        entry.getLongValue(), drops, target.level(), target.providerPos());
            }
        }
    }

    /** Clears all buffered outputs owned by this route. */
    @Override
    public void clearState(AdaptivePatternProviderDispatchTarget target) {
        target.clearRouteState();
    }

    private long availableAdmissions(AdaptivePatternProviderDispatchTarget target) {
        return Math.max(0L, (long) reusableWorkLimit(target) - target.reusableWorkCount() - target.pendingReusableOperations());
    }

    private boolean hasEnergy(AdaptivePatternProviderDispatchTarget target) {
        IEnergyService energy = target.energyService();
        if (energy == null) {
            return false;
        }
        double required = reusableEnergyPerWork(target);
        double extracted = energy.extractAEPower(required, Actionable.SIMULATE, PowerMultiplier.ONE);
        return extracted + ENERGY_TOLERANCE >= required;
    }

    private boolean consumeEnergy(AdaptivePatternProviderDispatchTarget target) {
        IEnergyService energy = target.energyService();
        if (energy == null) {
            return false;
        }
        double required = reusableEnergyPerWork(target);
        double extracted = energy.extractAEPower(required, Actionable.MODULATE, PowerMultiplier.ONE);
        if (extracted + ENERGY_TOLERANCE >= required) {
            return true;
        }
        energy.injectPower(extracted, Actionable.MODULATE);
        return false;
    }

    @Nullable
    private static List<GenericStack> patternOutput(
                                                    IMolecularAssemblerSupportedPattern pattern,
                                                    KeyCounter[] inputHolder,
                                                    ServerLevel level) {
        ItemStack[] grid = new ItemStack[9];
        Arrays.fill(grid, ItemStack.EMPTY);
        KeyCounter[] copy = copyKeyCounters(inputHolder);
        pattern.fillCraftingGrid(copy, (slot, stack) -> grid[slot] = stack);

        int minX = 3;
        int minY = 3;
        int maxX = -1;
        int maxY = -1;
        for (int slot = 0; slot < grid.length; slot++) {
            if (!grid[slot].isEmpty()) {
                int x = slot % 3;
                int y = slot / 3;
                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                maxX = Math.max(maxX, x);
                maxY = Math.max(maxY, y);
            }
        }
        if (maxX < 0) {
            return null;
        }

        int width = maxX - minX + 1;
        int height = maxY - minY + 1;
        ObjectArrayList<ItemStack> compressed = new ObjectArrayList<>(width * height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                compressed.add(grid[(minX + x) + (minY + y) * 3]);
            }
        }

        CraftingInput input = CraftingInput.of(width, height, compressed);
        ItemStack output = pattern.assemble(input, level);
        if (output == null || output.isEmpty()) {
            return null;
        }

        NonNullList<ItemStack> remainders = pattern.getRemainingItems(input);
        ObjectArrayList<GenericStack> result = new ObjectArrayList<>();
        GenericStack outputStack = GenericStack.fromItemStack(output);
        if (outputStack != null) {
            result.add(outputStack);
        }
        for (ItemStack remainder : remainders) {
            GenericStack remaining = GenericStack.fromItemStack(remainder);
            if (remaining != null) {
                result.add(remaining);
            }
        }
        return result;
    }

    private static KeyCounter[] copyKeyCounters(KeyCounter[] inputHolder) {
        KeyCounter[] copy = new KeyCounter[inputHolder.length];
        for (int index = 0; index < inputHolder.length; index++) {
            copy[index] = new KeyCounter();
            copy[index].addAll(inputHolder[index]);
        }
        return copy;
    }

    private static final class State {

        private final Object2LongOpenHashMap<AEKey> craftedContents = new Object2LongOpenHashMap<>();
    }
}
