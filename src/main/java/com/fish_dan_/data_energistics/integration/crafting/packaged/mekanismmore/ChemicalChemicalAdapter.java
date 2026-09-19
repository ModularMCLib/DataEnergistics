package com.fish_dan_.data_energistics.integration.crafting.packaged.mekanismmore;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.crafting.packaged.PackagedMachineAdapter;
import com.fish_dan_.data_energistics.api.crafting.packaged.PackagedMachineOperation;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.KeyCounter;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import com.jerry.meklm.common.tile.machine.TileEntityLargeChemicalInfuser;
import com.jerry.meklm.common.tile.machine.TileEntityLargePigmentMixer;
import com.jerry.meklm.common.tile.machine.TileEntityLargeSolarNeutronActivator;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import me.ramidzkh.mekae2.ae2.MekanismKey;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.recipes.ChemicalChemicalToChemicalRecipe;
import mekanism.api.recipes.ChemicalToChemicalRecipe;
import mekanism.api.recipes.ingredients.ChemicalStackIngredient;
import mekanism.api.recipes.vanilla_input.BiChemicalRecipeInput;
import mekanism.api.recipes.vanilla_input.SingleChemicalRecipeInput;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

/** Executes AMEK-backed chemical recipes while settling every chemical amount through the AE ledger. */
final class ChemicalChemicalAdapter implements PackagedMachineAdapter {

    enum Kind {
        INFUSER,
        PIGMENT_MIXER,
        SOLAR_ACTIVATOR
    }

    private final Kind kind;

    ChemicalChemicalAdapter(Kind kind) {
        this.kind = kind;
    }

    @Override
    public ResourceLocation id() {
        return Data_Energistics.id("mekanism_more_" + this.kind.name().toLowerCase(Locale.ROOT));
    }

    @Override
    public ObjectSet<ResourceLocation> recipeTypes() {
        return switch (this.kind) {
            case INFUSER -> ObjectSet.of(ResourceLocation.fromNamespaceAndPath("mekanism", "chemical_infusing"));
            case PIGMENT_MIXER -> ObjectSet.of(ResourceLocation.fromNamespaceAndPath("mekanism", "pigment_mixing"));
            case SOLAR_ACTIVATOR -> ObjectSet.of(ResourceLocation.fromNamespaceAndPath("mekanism", "activating"));
        };
    }

    @Override
    public boolean recognizes(ServerLevel level, BlockPos position) {
        if (!level.isLoaded(position)) return false;
        var blockEntity = level.getBlockEntity(position);
        return switch (this.kind) {
            case INFUSER -> blockEntity instanceof TileEntityLargeChemicalInfuser;
            case PIGMENT_MIXER -> blockEntity instanceof TileEntityLargePigmentMixer;
            case SOLAR_ACTIVATOR -> blockEntity instanceof TileEntityLargeSolarNeutronActivator;
        };
    }

    @Override
    public @Nullable CompoundTag prepare(ServerLevel level, BlockPos position, Direction face,
                                         ResourceLocation recipeId, IPatternDetails pattern, KeyCounter[] inputs) {
        if (!recognizes(level, position)) return null;
        var holder = level.getRecipeManager().byKey(recipeId);
        if (holder.isEmpty()) return null;
        CompoundTag progress = new CompoundTag();
        ChemicalStack output;
        if (this.kind == Kind.SOLAR_ACTIVATOR) {
            if (!(holder.get().value() instanceof ChemicalToChemicalRecipe recipe)) return null;
            ChemicalStack input = find(inputs, recipe.getInput());
            if (input == null || !recipe.matches(new SingleChemicalRecipeInput(input), level)) return null;
            output = recipe.getOutput(input);
            if (!matchesOutput(pattern, output)) return null;
            progress.put("input", input.saveOptional(level.registryAccess()));
        } else {
            if (!(holder.get().value() instanceof ChemicalChemicalToChemicalRecipe recipe)) return null;
            ChemicalStack left = find(inputs, recipe.getLeftInput());
            ChemicalStack right = find(inputs, recipe.getRightInput(), left);
            if (left == null || right == null || !recipe.matches(new BiChemicalRecipeInput(left, right), level)) return null;
            output = recipe.getOutput(left, right);
            if (!matchesOutput(pattern, output)) return null;
            progress.put("left", left.saveOptional(level.registryAccess()));
            progress.put("right", right.saveOptional(level.registryAccess()));
        }
        if (output.isEmpty()) return null;
        progress.put("output", output.saveOptional(level.registryAccess()));
        return progress;
    }

    @Override
    public boolean advance(PackagedMachineOperation operation) {
        if (!recognizes(operation.level(), operation.position())) return false;
        var progress = operation.progress();
        var output = ChemicalStack.parseOptional(operation.level().registryAccess(), progress.getCompound("output"));
        if (output.isEmpty()) throw new IllegalArgumentException("Missing persisted Mekanism chemical output");
        if (this.kind == Kind.SOLAR_ACTIVATOR) {
            var input = ChemicalStack.parseOptional(operation.level().registryAccess(), progress.getCompound("input"));
            if (input.isEmpty()) throw new IllegalArgumentException("Missing persisted Mekanism chemical input");
            if (!progress.getBoolean("delivered")) {
                operation.delivered(MekanismKey.of(input), input.getAmount());
                progress.putBoolean("delivered", true);
                operation.changed();
            }
        } else {
            var left = ChemicalStack.parseOptional(operation.level().registryAccess(), progress.getCompound("left"));
            var right = ChemicalStack.parseOptional(operation.level().registryAccess(), progress.getCompound("right"));
            if (left.isEmpty() || right.isEmpty()) throw new IllegalArgumentException("Missing persisted Mekanism chemical inputs");
            if (!progress.getBoolean("delivered")) {
                operation.delivered(MekanismKey.of(left), left.getAmount());
                operation.delivered(MekanismKey.of(right), right.getAmount());
                progress.putBoolean("delivered", true);
                operation.changed();
            }
        }
        operation.returned(MekanismKey.of(output), output.getAmount());
        operation.complete();
        return true;
    }

    private static @Nullable ChemicalStack find(KeyCounter[] inputs, ChemicalStackIngredient ingredient) {
        return find(inputs, ingredient, null);
    }

    private static @Nullable ChemicalStack find(KeyCounter[] inputs, ChemicalStackIngredient ingredient,
                                                @Nullable ChemicalStack excluded) {
        for (KeyCounter counter : inputs) {
            for (var entry : counter) {
                if (!(entry.getKey() instanceof MekanismKey key) || entry.getLongValue() <= 0) continue;
                ChemicalStack stack = key.getStack().copyWithAmount(ingredient.amount());
                if (!stack.isEmpty() && (excluded == null || !ChemicalStack.isSameChemical(stack, excluded)) && ingredient.test(stack)) {
                    return stack;
                }
            }
        }
        return null;
    }

    private static boolean matchesOutput(IPatternDetails pattern, ChemicalStack output) {
        if (pattern.getOutputs().size() != 1 || output.isEmpty()) return false;
        var declared = pattern.getOutputs().getFirst();
        return declared.what() instanceof MekanismKey key && ChemicalStack.isSameChemical(key.getStack(), output) && declared.amount() == output.getAmount();
    }
}
