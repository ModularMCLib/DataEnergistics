package com.fish_dan_.data_energistics.integration.ae.advancedae.reassembler;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.recipe.reassembler.DataReassemblerItemOutput;
import com.fish_dan_.data_energistics.recipe.reassembler.DataRipperReassemblerIngredient;
import com.fish_dan_.data_energistics.recipe.reassembler.DataRipperReassemblerRecipe;
import com.fish_dan_.data_energistics.recipe.reassembler.DataRipperReassemblerRecipeInput;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import net.pedroksl.advanced_ae.recipes.ReactionChamberRecipe;
import net.pedroksl.ae2addonlib.recipes.IngredientStack;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Set;

/** Reads Advanced AE's native reaction-chamber table for the data reassembler and its viewers. */
public final class AdvancedAeReassemblerRecipeSource {

    private AdvancedAeReassemblerRecipeSource() {}

    /** Returns all Advanced AE recipes represented by the data reassembler contract. */
    public static void appendRecipes(RecipeManager recipeManager,
                                     List<RecipeHolder<DataRipperReassemblerRecipe>> destination) {
        for (RecipeHolder<ReactionChamberRecipe> holder : recipeManager.getAllRecipesFor(ReactionChamberRecipe.TYPE)) {
            destination.addAll(adapt(holder));
        }
    }

    /** Returns the first native Advanced AE recipe that matches the supplied factory inputs. */
    public static @Nullable RecipeHolder<DataRipperReassemblerRecipe> findRecipe(
                                                                                 RecipeManager recipeManager,
                                                                                 DataRipperReassemblerRecipeInput input,
                                                                                 Level level,
                                                                                 Set<ResourceLocation> excludedRecipeIds) {
        for (RecipeHolder<ReactionChamberRecipe> holder : recipeManager.getAllRecipesFor(ReactionChamberRecipe.TYPE)) {
            for (RecipeHolder<DataRipperReassemblerRecipe> adapted : adapt(holder)) {
                if (!excludedRecipeIds.contains(adapted.id()) && adapted.value().matches(input, level)) {
                    return adapted;
                }
            }
        }
        return null;
    }

    /** Resolves a persisted factory recipe identifier from Advanced AE's current native table. */
    public static @Nullable RecipeHolder<DataRipperReassemblerRecipe> findById(
                                                                               RecipeManager recipeManager,
                                                                               ResourceLocation recipeId) {
        for (RecipeHolder<ReactionChamberRecipe> holder : recipeManager.getAllRecipesFor(ReactionChamberRecipe.TYPE)) {
            for (RecipeHolder<DataRipperReassemblerRecipe> adapted : adapt(holder)) {
                if (adapted.id().equals(recipeId)) {
                    return adapted;
                }
            }
        }
        return null;
    }

    private static List<RecipeHolder<DataRipperReassemblerRecipe>> adapt(RecipeHolder<ReactionChamberRecipe> holder) {
        try {
            ReactionChamberRecipe source = holder.value();
            List<DataRipperReassemblerIngredient> itemInputs = new ObjectArrayList<>();
            for (IngredientStack.Item input : source.getInputs()) {
                if (!input.isEmpty()) {
                    itemInputs.add(new DataRipperReassemblerIngredient(input.getIngredient(), input.getAmount()));
                }
            }

            IngredientStack.Fluid fluid = source.getFluid();
            List<List<GenericStack>> fluidVariants = createFluidVariants(fluid);
            GenericStack output = source.output;
            if (output == null || output.what() == null || output.amount() <= 0L) {
                throw new IllegalArgumentException("Reaction chamber output must be a positive resource");
            }

            List<DataReassemblerItemOutput> itemOutputs = List.of();
            List<GenericStack> fluidOutputs = List.of();
            GenericStack keyOutput = null;
            if (output.what() instanceof AEItemKey itemKey) {
                if (output.amount() > Integer.MAX_VALUE) {
                    throw new IllegalArgumentException("Reaction chamber item output exceeds integer capacity");
                }
                itemOutputs = List.of(new DataReassemblerItemOutput(
                        itemKey.toStack((int) output.amount()), null));
            } else if (output.what() instanceof AEFluidKey) {
                fluidOutputs = List.of(output);
            } else {
                keyOutput = output;
            }

            List<RecipeHolder<DataRipperReassemblerRecipe>> variants = new ObjectArrayList<>(fluidVariants.size());
            for (int variantIndex = 0; variantIndex < fluidVariants.size(); variantIndex++) {
                DataRipperReassemblerRecipe recipe = new DataRipperReassemblerRecipe(
                        itemInputs,
                        fluidVariants.get(variantIndex),
                        itemOutputs,
                        fluidOutputs,
                        DataRipperReassemblerRecipe.PROCESS_TICKS,
                        null,
                        keyOutput);
                variants.add(new RecipeHolder<>(adaptedId(holder.id(), variantIndex), recipe));
            }
            return variants;
        } catch (IllegalArgumentException exception) {
            Data_Energistics.LOGGER.warn(
                    "Skipped Advanced AE reaction-chamber recipe {} because it cannot be represented by the data reassembler: {}",
                    holder.id(), exception.getMessage());
            return List.of();
        }
    }

    private static List<List<GenericStack>> createFluidVariants(IngredientStack.@Nullable Fluid fluid) {
        if (fluid == null || fluid.isEmpty()) {
            return List.of(List.of());
        }
        FluidIngredient ingredient = fluid.getIngredient();
        List<List<GenericStack>> variants = new ObjectArrayList<>();
        for (FluidStack candidate : ingredient.getStacks()) {
            if (!candidate.isEmpty()) {
                variants.add(List.of(new GenericStack(AEFluidKey.of(candidate), fluid.getAmount())));
            }
        }
        if (variants.isEmpty()) {
            throw new IllegalArgumentException("Reaction chamber fluid ingredient has no registered fluids");
        }
        return variants;
    }

    private static ResourceLocation adaptedId(ResourceLocation sourceRecipeId, int variantIndex) {
        return ResourceLocation.fromNamespaceAndPath(
                Data_Energistics.MODID,
                "asynchronous_factory/aae_reaction/" + sourceRecipeId.getNamespace() + "/" +
                        sourceRecipeId.getPath() + "/" + variantIndex);
    }
}
