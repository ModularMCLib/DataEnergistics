package com.fish_dan_.data_energistics.integration.crafting.packaged.mekanismmore;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.crafting.packaged.PackagedMachineAdapter;
import com.fish_dan_.data_energistics.api.crafting.packaged.PackagedMachineOperation;

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
        return level.isLoaded(position) && kind.layout(level.getBlockEntity(position)) != null;
    }

    @Override
    public @Nullable CompoundTag prepare(ServerLevel level, BlockPos position, Direction face,
                                         ResourceLocation recipeId, IPatternDetails pattern, KeyCounter[] inputs) {
        if (!level.isLoaded(position)) return null;
        var layout = kind.layout(level.getBlockEntity(position));
        if (layout == null || !layout.empty() || !layout.operatingModeValid()) return null;
        var plan = LargeMachineRecipePlan.prepare(level, layout, nativeRecipeId(recipeId), pattern, inputs);
        if (plan == null || !fits(layout.inputs(), plan.inputs()) || !outputFits(layout, plan)) return null;
        var progress = plan.save(level.registryAccess());
        progress.putBoolean("fluid_to_chemical", layout.fluidToChemical());
        return progress;
    }

    @Override
    public boolean advance(PackagedMachineOperation operation) {
        var layout = kind.layout(operation.level().getBlockEntity(operation.position()));
        if (layout == null) return false;
        var progress = operation.progress();
        // Old drafts never stored physical resources. Refuse their progress instead of manufacturing an output.
        var plan = LargeMachineRecipePlan.load(progress, operation.level().registryAccess());
        if (progress.getBoolean("fluid_to_chemical") != layout.fluidToChemical() || !layout.operatingModeValid()) return false;
        if (layout.inputs().size() != plan.inputs().size() || layout.outputs().size() != plan.outputs().size()) {
            throw new IllegalStateException("Mekanism machine resource layout changed");
        }
        if (!progress.getBoolean("in_machine")) {
            if (!layout.empty() || !fits(layout.inputs(), plan.inputs()) || !outputFits(layout, plan)) return false;
            var holder = operation.level().getRecipeManager().byKey(nativeRecipeId(operation.recipeId()));
            if (holder.isEmpty()) throw new IllegalStateException("Mekanism recipe disappeared");
            var unit = LargeMachineRecipePlan.unit(holder.get().value(), plan.inputs().getFirst().what(),
                    plan.inputs().getLast().what(), layout.fluidToChemical());
            if (unit == null || !unit.inputs().equals(plan.inputs()) || !unit.outputs().equals(plan.outputs())) {
                throw new IllegalStateException("Mekanism recipe changed after admission");
            }
            for (var entry : LargeMachineRecipePlan.totals(plan.inputs()).entrySet()) {
                if (operation.available(entry.getKey()).compareTo(BigInteger.valueOf(entry.getValue())) < 0) {
                    throw new IllegalStateException("Mekanism input ledger does not cover this cycle");
                }
            }
            for (int i = 0; i < plan.inputs().size(); i++) {
                var stack = plan.inputs().get(i);
                long accepted = layout.inputs().get(i).insert(stack, Action.EXECUTE);
                if (accepted > 0) operation.delivered(stack.what(), accepted);
                if (accepted != stack.amount()) throw new IllegalStateException("Mekanism input changed after simulation");
            }
            progress.putBoolean("in_machine", true);
            if (kind == LargeMachineKind.ROTARY) {
                layout.tile().getPersistentData().putUUID("de_packaged_rotary_owner", operation.id());
                layout.tile().getPersistentData().putBoolean("de_packaged_rotary_mode", layout.fluidToChemical());
            }
            operation.changed();
            if (layout.tile().getRecipe(0) != holder.get().value()) {
                throw new IllegalStateException("Mekanism native machine selected a different recipe");
            }
            layout.tile().setChanged();
            return true;
        }
        // Wait for all native output ports and consumed inputs, including per-tick nuclear chemicals.
        for (int i = 0; i < plan.outputs().size(); i++) {
            var actual = layout.outputs().get(i).contents();
            if (actual == null) return false;
            if (!actual.equals(plan.outputs().get(i))) throw new IllegalStateException("Unexpected Mekanism machine output");
        }
        for (var input : layout.inputs()) if (input.contents() != null) return false;
        for (int i = 0; i < plan.outputs().size(); i++) {
            var stack = plan.outputs().get(i);
            long extracted = layout.outputs().get(i).extract(stack.amount());
            if (extracted > 0) operation.returned(stack.what(), extracted);
            if (extracted != stack.amount()) throw new IllegalStateException("Mekanism output extraction was incomplete");
        }
        progress.putBoolean("in_machine", false);
        progress.putLong("cycles", plan.cycles() - 1);
        operation.changed();
        layout.tile().setChanged();
        if (plan.cycles() == 1) operation.complete();
        return true;
    }

    private ResourceLocation nativeRecipeId(ResourceLocation id) {
        if (kind == LargeMachineKind.ROTARY) {
            for (String prefix : List.of("/condensentrating/", "/decondensentrating/")) {
                if (id.getPath().startsWith(prefix)) return ResourceLocation.fromNamespaceAndPath(id.getNamespace(), id.getPath().substring(prefix.length()));
            }
        }
        return id;
    }

    private static boolean fits(List<MachineResourcePort> ports, List<GenericStack> stacks) {
        if (ports.size() != stacks.size()) return false;
        for (int i = 0; i < ports.size(); i++) if (ports.get(i).insert(stacks.get(i), Action.SIMULATE) != stacks.get(i).amount()) return false;
        return true;
    }

    private static boolean outputFits(LargeMachineKind.Layout layout, LargeMachineRecipePlan plan) {
        if (layout.outputs().size() != plan.outputs().size()) return false;
        for (int i = 0; i < plan.outputs().size(); i++) if (!layout.outputs().get(i).canHold(plan.outputs().get(i))) return false;
        return true;
    }
}
