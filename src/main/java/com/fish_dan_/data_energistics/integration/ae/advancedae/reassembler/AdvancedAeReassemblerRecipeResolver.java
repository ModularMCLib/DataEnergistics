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
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.jspecify.annotations.Nullable;

/** Resolves Advanced AE's native reaction-chamber recipes for the asynchronous factory. */
public final class AdvancedAeReassemblerRecipeResolver {

    private AdvancedAeReassemblerRecipeResolver() {}

    /** Returns the first native Advanced AE recipe that matches the supplied factory inputs. */
    public static @Nullable RecipeHolder<DataRipperReassemblerRecipe> findRecipe(
                                                                                 RecipeManager recipeManager,
                                                                                 DataRipperReassemblerRecipeInput input,
                                                                                 Level level,
                                                                                 ObjectSet<ResourceLocation> excludedRecipeIds) {
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
                                                                               ResourceLocation recipeId,
                                                                               DataRipperReassemblerRecipeInput input,
                                                                               Level level) {
        for (RecipeHolder<ReactionChamberRecipe> holder : recipeManager.getAllRecipesFor(ReactionChamberRecipe.TYPE)) {
            for (RecipeHolder<DataRipperReassemblerRecipe> adapted : adapt(holder)) {
                if (adapted.id().equals(recipeId) && adapted.value().matches(input, level)) {
                    return adapted;
                }
            }
        }
        return null;
    }

    private static ObjectList<RecipeHolder<DataRipperReassemblerRecipe>> adapt(RecipeHolder<ReactionChamberRecipe> holder) {
        try {
            ReactionChamberRecipe source = holder.value();
            ObjectList<DataRipperReassemblerIngredient> itemInputs = new ObjectArrayList<>();
            for (IngredientStack.Item input : source.getInputs()) {
                if (!input.isEmpty()) {
                    itemInputs.add(new DataRipperReassemblerIngredient(input.getIngredient(), input.getAmount()));
                }
            }

            IngredientStack.Fluid fluid = source.getFluid();
            ObjectList<ObjectList<GenericStack>> fluidVariants = createFluidVariants(fluid);
            GenericStack output = source.output;
            if (output == null || output.what() == null || output.amount() <= 0L) {
                throw new IllegalArgumentException("Reaction chamber output must be a positive resource");
            }

            ObjectList<DataReassemblerItemOutput> itemOutputs = ObjectLists.emptyList();
            ObjectList<GenericStack> fluidOutputs = ObjectLists.emptyList();
            GenericStack keyOutput = null;
            if (output.what() instanceof AEItemKey itemKey) {
                if (output.amount() > Integer.MAX_VALUE) {
                    throw new IllegalArgumentException("Reaction chamber item output exceeds integer capacity");
                }
                itemOutputs = ObjectLists.singleton(new DataReassemblerItemOutput(
                        itemKey.toStack((int) output.amount()), null));
            } else if (output.what() instanceof AEFluidKey) {
                fluidOutputs = ObjectLists.singleton(output);
            } else {
                keyOutput = output;
            }

            ObjectList<RecipeHolder<DataRipperReassemblerRecipe>> variants = new ObjectArrayList<>(fluidVariants.size());
            for (ObjectList<GenericStack> fluidVariant : fluidVariants) {
                DataRipperReassemblerRecipe recipe = new DataRipperReassemblerRecipe(
                        itemInputs,
                        fluidVariant,
                        itemOutputs,
                        fluidOutputs,
                        DataRipperReassemblerRecipe.PROCESS_TICKS,
                        null,
                        keyOutput);
                variants.add(new RecipeHolder<>(holder.id(), recipe));
            }
            return ObjectLists.unmodifiable(variants);
        } catch (IllegalArgumentException exception) {
            Data_Energistics.LOGGER.warn(
                    "Skipped Advanced AE reaction-chamber recipe {} because it cannot be represented by the data reassembler: {}",
                    holder.id(), exception.getMessage());
            return ObjectLists.emptyList();
        }
    }

    private static ObjectList<ObjectList<GenericStack>> createFluidVariants(IngredientStack.@Nullable Fluid fluid) {
        if (fluid == null || fluid.isEmpty()) {
            return ObjectLists.singleton(ObjectLists.emptyList());
        }
        FluidIngredient ingredient = fluid.getIngredient();
        ObjectList<ObjectList<GenericStack>> variants = new ObjectArrayList<>();
        for (FluidStack candidate : ingredient.getStacks()) {
            if (!candidate.isEmpty()) {
                variants.add(ObjectLists.singleton(new GenericStack(AEFluidKey.of(candidate), fluid.getAmount())));
            }
        }
        if (variants.isEmpty()) {
            throw new IllegalArgumentException("Reaction chamber fluid ingredient has no registered fluids");
        }
        return ObjectLists.unmodifiable(variants);
    }
}
