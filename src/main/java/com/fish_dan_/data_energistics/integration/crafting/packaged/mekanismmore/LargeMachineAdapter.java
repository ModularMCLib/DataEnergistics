package com.fish_dan_.data_energistics.integration.crafting.packaged.mekanismmore;

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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import me.ramidzkh.mekae2.ae2.MekanismKey;
import mekanism.api.Action;
import mekanism.api.recipes.NucleosynthesizingRecipe;
import org.jspecify.annotations.Nullable;

import java.math.BigInteger;
import java.util.List;
import java.util.Locale;

/** Feeds bounded cycles into real machines; only physically extracted resources enter the return ledger. */
final class LargeMachineAdapter implements PackagedMachineAdapter {

    private final LargeMachineKind kind;

    LargeMachineAdapter(LargeMachineKind kind) {
        this.kind = kind;
    }

    @Override
    public ResourceLocation id() {
        return Data_Energistics.id("mekanism_more_" + kind.name().toLowerCase(Locale.ROOT));
    }

    @Override
    public ObjectSet<ResourceLocation> recipeTypes() {
        return kind == LargeMachineKind.ROTARY ? ObjectSet.of(kind.recipeType,
                ResourceLocation.fromNamespaceAndPath("mekanism", "condensentrating"),
                ResourceLocation.fromNamespaceAndPath("mekanism", "decondensentrating")) : ObjectSet.of(kind.recipeType);
    }

    @Override
    public @Nullable ItemStack completeEncoding(ServerLevel level, ResourceLocation recipeId, ItemStack encodedPattern) {
        if (kind != LargeMachineKind.NUCLEOSYNTHESIZER) return encodedPattern;
        var holder = level.getRecipeManager().byKey(recipeId);
        if (holder.isEmpty() || !(holder.get().value() instanceof NucleosynthesizingRecipe recipe)) return null;
        if (!recipe.perTickUsage()) return encodedPattern;
        var component = encodedPattern.get(AEComponents.ENCODED_PROCESSING_PATTERN);
        if (component == null) return null;
        var stacks = new ObjectArrayList<GenericStack>();
        for (var input : component.sparseInputs()) if (input != null) stacks.add(input);
        if (stacks.size() != 2) return null;
        GenericStack item = null;
        int gasIndex = -1;
        for (int i = 0; i < stacks.size(); i++) {
            if (stacks.get(i).what() instanceof AEItemKey) item = stacks.get(i);
            if (stacks.get(i).what() instanceof MekanismKey) gasIndex = i;
        }
        if (item == null || gasIndex < 0) return null;
        var unit = LargeMachineRecipePlan.unit(recipe, item.what(), stacks.get(gasIndex).what(), false);
        if (unit == null || item.amount() <= 0 || item.amount() % unit.inputs().getFirst().amount() != 0) return null;
        try {
            long cycles = item.amount() / unit.inputs().getFirst().amount();
            stacks.set(gasIndex, new GenericStack(stacks.get(gasIndex).what(), Math.multiplyExact(cycles, unit.inputs().getLast().amount())));
            var copy = encodedPattern.copy();
            copy.set(AEComponents.ENCODED_PROCESSING_PATTERN, new EncodedProcessingPattern(stacks, component.sparseOutputs()));
            return copy;
        } catch (ArithmeticException exception) {
            return null;
        }
    }

    @Override
    public boolean recognizes(ServerLevel level, BlockPos position) {
        return kind.layout(level, position) != null;
    }

    @Override
    public long batchCapacity(ServerLevel level, BlockPos position, Direction face, ResourceLocation recipeId,
                              IPatternDetails pattern, KeyCounter[] prototype, long requestedCount) {
        var layout = kind.layout(level, position);
        if (layout == null) return 0;
        var preparation = prepare(level, layout, recipeId, pattern, prototype);
        if (preparation == null) return 0;
        var plan = LargeMachineRecipePlan.load(preparation, level.registryAccess());
        long capacity = requestedCount;
        for (int i = 0; i < plan.inputs().size(); i++) {
            var input = plan.inputs().get(i);
            long accepted = layout.inputs().get(i).insert(new GenericStack(input.what(), Long.MAX_VALUE), Action.SIMULATE);
            long perCycle = preparation.getBoolean("per_tick_chemical") && i == 1 ?
                    preparation.getLong("chemical_feed_minimum") : input.amount();
            capacity = Math.min(capacity, accepted / perCycle / plan.cycles());
        }
        for (int i = 0; i < plan.outputs().size(); i++) {
            var output = plan.outputs().get(i);
            capacity = Math.min(capacity, layout.outputs().get(i).capacity(output.what()) / output.amount() / plan.cycles());
        }
        // One encoded craft may itself contain multiple native cycles; stream it through the native ports.
        return Math.max(1, capacity);
    }

    @Override
    public @Nullable CompoundTag prepare(ServerLevel level, BlockPos position, Direction face,
                                         ResourceLocation recipeId, IPatternDetails pattern, KeyCounter[] inputs) {
        var layout = kind.layout(level, position);
        return layout == null ? null : prepare(level, layout, recipeId, pattern, inputs);
    }

    private @Nullable CompoundTag prepare(ServerLevel level, LargeMachineKind.Layout layout,
                                         ResourceLocation recipeId, IPatternDetails pattern, KeyCounter[] inputs) {
        if (!layout.empty() || !layout.operatingModeValid()) return null;
        var plan = LargeMachineRecipePlan.prepare(level, layout, nativeRecipeId(recipeId), pattern, inputs);
        if (plan == null) return null;
        long chemicalMinimum = plan.inputs().getLast().amount();
        var holder = level.getRecipeManager().byKey(nativeRecipeId(recipeId));
        boolean perTickChemical = false;
        if (holder.isPresent() && holder.get().value() instanceof NucleosynthesizingRecipe nuclear && nuclear.perTickUsage()) {
            perTickChemical = true;
            chemicalMinimum /= nuclear.getDuration();
        }
        if (!fits(layout.inputs(), plan.inputs(), chemicalMinimum) || !outputFits(layout, plan)) return null;
        var progress = plan.save(level.registryAccess());
        progress.putLong("chemical_feed_minimum", chemicalMinimum);
        progress.putBoolean("per_tick_chemical", perTickChemical);
        progress.putLong("machine_position", layout.tile().getBlockPos().asLong());
        progress.putBoolean("fluid_to_chemical", layout.fluidToChemical());
        return progress;
    }

    @Override
    public ObjectList<BlockPos> occupiedPositions(ServerLevel level, BlockPos position, CompoundTag preparation) {
        return kind.occupiedPositions(position, preparation);
    }

    @Override
    public boolean advance(PackagedMachineOperation operation) {
        var layout = kind.runningLayout(operation.level(), operation.position(), operation.progress());
        if (layout == null) return false;
        var progress = operation.progress();
        // Old drafts never stored physical resources. Refuse their progress instead of manufacturing an output.
        var plan = LargeMachineRecipePlan.load(progress, operation.level().registryAccess());
        long concurrentCycles = plan.cycles();
        if (progress.getBoolean("fluid_to_chemical") != layout.fluidToChemical() || !layout.operatingModeValid()) return false;
        if (layout.inputs().size() != plan.inputs().size() || layout.outputs().size() != plan.outputs().size()) {
            throw new IllegalStateException("Mekanism machine resource layout changed");
        }
        if (!progress.getBoolean("in_machine")) {
            long chemicalMinimum = progress.getLong("chemical_feed_minimum");
            if (!layout.empty() || !fits(layout.inputs(), plan.inputs(), chemicalMinimum) || !outputFits(layout, plan)) return false;
            var holder = operation.level().getRecipeManager().byKey(nativeRecipeId(operation.recipeId()));
            if (holder.isEmpty()) throw new IllegalStateException("Mekanism recipe disappeared");
            var unit = LargeMachineRecipePlan.unit(holder.get().value(), plan.inputs().getFirst().what(),
                    plan.inputs().getLast().what(), layout.fluidToChemical());
            if (unit == null || !unit.inputs().equals(plan.inputs()) ||
                    !PackagedOutputMatching.matchesResources(operation, plan.outputs(), unit.outputs())) {
                throw new IllegalStateException("Mekanism recipe changed after admission");
            }
            boolean perTickChemical = holder.get().value() instanceof NucleosynthesizingRecipe nuclear && nuclear.perTickUsage();
            if (perTickChemical != progress.getBoolean("per_tick_chemical")) {
                throw new IllegalStateException("Mekanism chemical consumption mode changed after admission");
            }
            for (var entry : LargeMachineRecipePlan.totals(plan.inputs()).object2LongEntrySet()) {
                if (operation.available(entry.getKey()).compareTo(BigInteger.valueOf(entry.getLongValue()).multiply(BigInteger.valueOf(concurrentCycles))) < 0) {
                    throw new IllegalStateException("Mekanism input ledger does not cover this native batch");
                }
            }
            progress.putLongArray("input_delivered", new long[plan.inputs().size()]);
            progress.putLongArray("output_collected", new long[plan.outputs().size()]);
            progress.putBoolean("in_machine", true);
            if (kind == LargeMachineKind.ROTARY) {
                layout.tile().getPersistentData().putUUID("de_packaged_rotary_owner", operation.id());
                layout.tile().getPersistentData().putBoolean("de_packaged_rotary_mode", layout.fluidToChemical());
            }
            operation.changed();
            feed(operation, layout, plan, concurrentCycles);
            var selectedRecipe = layout.tile().getRecipe(0);
            var selectedPlan = selectedRecipe == null ? null : LargeMachineRecipePlan.unit(selectedRecipe,
                    plan.inputs().getFirst().what(), plan.inputs().getLast().what(), layout.fluidToChemical());
            if (selectedPlan == null || !selectedPlan.inputs().equals(plan.inputs()) ||
                    !PackagedOutputMatching.matchesResources(operation, plan.outputs(), selectedPlan.outputs())) {
                throw new IllegalStateException("Mekanism native machine selected a different recipe");
            }
            layout.tile().setChanged();
            return true;
        }
        boolean changed = !progress.getBoolean("finishing") && feed(operation, layout, plan, concurrentCycles);
        var collected = progress.getLongArray("output_collected");
        if (collected.length != plan.outputs().size()) throw new IllegalArgumentException("Invalid Mekanism output ledger");
        // Drain independently: a filled first output must not block arrival of the second output.
        for (int i = 0; i < plan.outputs().size(); i++) {
            var expected = plan.outputs().get(i);
            long expectedAmount = Math.multiplyExact(expected.amount(), concurrentCycles);
            if (collected[i] < 0 || collected[i] > expectedAmount) throw new IllegalArgumentException("Invalid Mekanism collected amount");
            var actual = layout.outputs().get(i).contents();
            if (actual == null) continue;
            if (actual.amount() > expectedAmount - collected[i] ||
                    !PackagedOutputMatching.matches(operation, new GenericStack(expected.what(), actual.amount()), actual)) {
                throw new IllegalStateException("Unexpected Mekanism machine output");
            }
            long extracted = layout.outputs().get(i).extract(actual.amount());
            if (extracted > 0) {
                operation.returned(actual.what(), extracted);
                collected[i] += extracted;
                progress.putLongArray("output_collected", collected);
                operation.changed();
                layout.tile().setChanged();
                changed = true;
            }
            if (extracted != actual.amount()) throw new IllegalStateException("Mekanism output extraction was incomplete");
        }
        for (int i = 0; i < collected.length; i++) {
            if (collected[i] != Math.multiplyExact(plan.outputs().get(i).amount(), concurrentCycles)) return changed;
        }
        var delivered = delivered(progress, plan, concurrentCycles);
        for (int i = 0; i < delivered.length; i++) {
            if (progress.getBoolean("per_tick_chemical") && i == 1) continue;
            if (delivered[i] != Math.multiplyExact(plan.inputs().get(i).amount(), concurrentCycles)) return changed;
        }
        if (progress.getBoolean("per_tick_chemical")) {
            // Native constant-use accounting is shared across concurrent operations, so the encoded
            // duration-based chemical quantity is a budget ceiling, not a promise to consume it all.
            if (layout.inputs().getFirst().contents() != null) return changed;
            if (!progress.getBoolean("finishing")) {
                progress.putBoolean("finishing", true);
                operation.changed();
            }
            returnChemicalBudget(operation, layout, plan, concurrentCycles);
        }
        for (var input : layout.inputs()) if (input.contents() != null) return changed;
        operation.complete();
        layout.tile().setChanged();
        return true;
    }

    private static void returnChemicalBudget(PackagedMachineOperation operation, LargeMachineKind.Layout layout,
                                             LargeMachineRecipePlan plan, long cycles) {
        var chemical = plan.inputs().getLast();
        var progress = operation.progress();
        long delivered = delivered(progress, plan, cycles)[1];
        long recovered = progress.getLong("chemical_recovered");
        long refunded = progress.getLong("chemical_unassigned_refunded");
        long budget = Math.multiplyExact(chemical.amount(), cycles);
        if (recovered < 0 || recovered > delivered || refunded < 0 || refunded > budget - delivered) {
            throw new IllegalArgumentException("Invalid Mekanism chemical refund ledger");
        }
        var port = layout.inputs().getLast();
        var actual = port.contents();
        if (actual != null) {
            if (!actual.what().equals(chemical.what()) || actual.amount() > delivered - recovered) {
                throw new IllegalStateException("Unexpected remaining Mekanism chemical budget");
            }
            long extracted = port.extract(actual.amount());
            if (extracted > 0) {
                operation.returned(actual.what(), extracted);
                progress.putLong("chemical_recovered", Math.addExact(recovered, extracted));
                operation.changed();
                layout.tile().setChanged();
            }
            if (extracted != actual.amount()) throw new IllegalStateException("Mekanism chemical budget extraction was incomplete");
        }
        long unassigned = operation.available(chemical.what()).longValueExact();
        if (unassigned != budget - delivered - refunded) {
            throw new IllegalStateException("Mekanism chemical budget differs from its accepted envelope");
        }
        if (unassigned > 0) {
            operation.delivered(chemical.what(), unassigned);
            operation.returned(chemical.what(), unassigned);
            progress.putLong("chemical_unassigned_refunded", Math.addExact(refunded, unassigned));
            operation.changed();
        }
    }

    private ResourceLocation nativeRecipeId(ResourceLocation id) {
        if (kind == LargeMachineKind.ROTARY) {
            for (String prefix : List.of("/condensentrating/", "/decondensentrating/")) {
                if (id.getPath().startsWith(prefix)) return ResourceLocation.fromNamespaceAndPath(id.getNamespace(), id.getPath().substring(prefix.length()));
            }
        }
        return id;
    }

    private boolean fits(List<MachineResourcePort> ports, List<GenericStack> stacks, long chemicalMinimum) {
        if (ports.size() != stacks.size()) return false;
        for (int i = 0; i < ports.size(); i++) {
            long accepted = ports.get(i).insert(stacks.get(i), Action.SIMULATE);
            long minimum = kind == LargeMachineKind.NUCLEOSYNTHESIZER && i == 1 ? chemicalMinimum : stacks.get(i).amount();
            if (minimum <= 0 || accepted < minimum) return false;
        }
        return true;
    }

    private static long[] delivered(CompoundTag progress, LargeMachineRecipePlan plan, long concurrentCycles) {
        var delivered = progress.getLongArray("input_delivered");
        if (delivered.length != plan.inputs().size()) throw new IllegalArgumentException("Invalid Mekanism input ledger");
        for (int i = 0; i < delivered.length; i++) {
            if (delivered[i] < 0 || delivered[i] > Math.multiplyExact(plan.inputs().get(i).amount(), concurrentCycles)) throw new IllegalArgumentException("Invalid Mekanism delivered amount");
        }
        return delivered;
    }

    private static boolean feed(PackagedMachineOperation operation, LargeMachineKind.Layout layout, LargeMachineRecipePlan plan, long concurrentCycles) {
        var delivered = delivered(operation.progress(), plan, concurrentCycles);
        boolean changed = false;
        for (int i = 0; i < delivered.length; i++) {
            var input = plan.inputs().get(i);
            long remaining = Math.multiplyExact(input.amount(), concurrentCycles) - delivered[i];
            if (remaining == 0) continue;
            if (operation.available(input.what()).compareTo(BigInteger.valueOf(remaining)) < 0) {
                throw new IllegalStateException("Mekanism input ledger does not cover pending delivery");
            }
            long accepted = layout.inputs().get(i).insert(new GenericStack(input.what(), remaining), Action.EXECUTE);
            if (accepted > 0) {
                operation.delivered(input.what(), accepted);
                delivered[i] += accepted;
                operation.progress().putLongArray("input_delivered", delivered);
                operation.changed();
                layout.tile().setChanged();
                changed = true;
            }
        }
        return changed;
    }

    private static boolean outputFits(LargeMachineKind.Layout layout, LargeMachineRecipePlan plan) {
        if (layout.outputs().size() != plan.outputs().size()) return false;
        for (int i = 0; i < plan.outputs().size(); i++) if (!layout.outputs().get(i).canHold(plan.outputs().get(i))) return false;
        return true;
    }
}
