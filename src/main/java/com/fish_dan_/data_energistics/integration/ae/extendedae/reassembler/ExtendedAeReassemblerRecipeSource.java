package com.fish_dan_.data_energistics.integration.ae.extendedae.reassembler;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.recipe.reassembler.DataReassemblerItemOutput;
import com.fish_dan_.data_energistics.recipe.reassembler.DataRipperReassemblerIngredient;
import com.fish_dan_.data_energistics.recipe.reassembler.DataRipperReassemblerRecipe;
import com.fish_dan_.data_energistics.recipe.reassembler.DataRipperReassemblerRecipeInput;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.GenericStack;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;

import com.glodblock.github.extendedae.recipe.CrystalAssemblerRecipe;
import com.glodblock.github.glodium.recipe.stack.IngredientStack;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Set;

/** Reads ExtendedAE's native crystal-assembler table for the data reassembler and its viewers. */
public final class ExtendedAeReassemblerRecipeSource {

    private ExtendedAeReassemblerRecipeSource() {}

    /** Returns all ExtendedAE recipes represented by the data reassembler contract. */
    public static void appendRecipes(RecipeManager recipeManager,
                                     List<RecipeHolder<DataRipperReassemblerRecipe>> destination) {
        for (RecipeHolder<CrystalAssemblerRecipe> holder : recipeManager.getAllRecipesFor(CrystalAssemblerRecipe.TYPE)) {
            destination.addAll(adapt(holder));
        }
    }

    /** Returns the first native ExtendedAE recipe that matches the supplied factory inputs. */
    public static @Nullable RecipeHolder<DataRipperReassemblerRecipe> findRecipe(
                                                                                 RecipeManager recipeManager,
                                                                                 DataRipperReassemblerRecipeInput input,
                                                                                 Level level,
                                                                                 Set<ResourceLocation> excludedRecipeIds) {
        for (RecipeHolder<CrystalAssemblerRecipe> holder : recipeManager.getAllRecipesFor(CrystalAssemblerRecipe.TYPE)) {
            for (RecipeHolder<DataRipperReassemblerRecipe> adapted : adapt(holder)) {
                if (!excludedRecipeIds.contains(adapted.id()) && adapted.value().matches(input, level)) {
                    return adapted;
                }
            }
        }
        return null;
    }

    /** Resolves a persisted factory recipe identifier from ExtendedAE's current native table. */
    public static @Nullable RecipeHolder<DataRipperReassemblerRecipe> findById(
                                                                               RecipeManager recipeManager,
                                                                               ResourceLocation recipeId) {
        for (RecipeHolder<CrystalAssemblerRecipe> holder : recipeManager.getAllRecipesFor(CrystalAssemblerRecipe.TYPE)) {
            for (RecipeHolder<DataRipperReassemblerRecipe> adapted : adapt(holder)) {
                if (adapted.id().equals(recipeId)) {
                    return adapted;
                }
            }
        }
        return null;
    }

    private static List<RecipeHolder<DataRipperReassemblerRecipe>> adapt(RecipeHolder<CrystalAssemblerRecipe> holder) {
        try {
            CrystalAssemblerRecipe source = holder.value();
            ItemStack output = source.output;
            if (output.isEmpty()) {
                throw new IllegalArgumentException("Crystal assembler output must not be empty");
            }

            List<DataRipperReassemblerIngredient> itemInputs = new ObjectArrayList<>();
            for (IngredientStack.Item input : source.getInputs()) {
                if (!input.isEmpty()) {
                    itemInputs.add(new DataRipperReassemblerIngredient(input.getIngredient(), input.getAmount()));
                }
            }

            IngredientStack.@Nullable Fluid fluid = source.getFluid();
            List<List<GenericStack>> fluidVariants = createFluidVariants(fluid);
            List<RecipeHolder<DataRipperReassemblerRecipe>> variants = new ObjectArrayList<>(fluidVariants.size());
            for (int variantIndex = 0; variantIndex < fluidVariants.size(); variantIndex++) {
                DataRipperReassemblerRecipe recipe = new DataRipperReassemblerRecipe(
                        itemInputs,
                        fluidVariants.get(variantIndex),
                        List.of(new DataReassemblerItemOutput(output.copy(), null)),
                        List.of(),
                        DataRipperReassemblerRecipe.PROCESS_TICKS,
                        null,
                        null);
                variants.add(new RecipeHolder<>(adaptedId(holder.id(), variantIndex), recipe));
            }
            return variants;
        } catch (IllegalArgumentException exception) {
            Data_Energistics.LOGGER.warn(
                    "Skipped ExtendedAE crystal assembler recipe {} because it cannot be represented by the data reassembler: {}",
                    holder.id(), exception.getMessage());
            return List.of();
        }
    }

    private static List<List<GenericStack>> createFluidVariants(
                                                                IngredientStack.@Nullable Fluid fluid) {
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
            throw new IllegalArgumentException("Crystal assembler fluid ingredient has no registered fluids");
        }
        return variants;
    }

    private static ResourceLocation adaptedId(ResourceLocation sourceRecipeId, int variantIndex) {
        return ResourceLocation.fromNamespaceAndPath(
                Data_Energistics.MODID,
                "asynchronous_factory/eae_crystal/" + sourceRecipeId.getNamespace() + "/" +
                        sourceRecipeId.getPath() + "/" + variantIndex);
    }
}
