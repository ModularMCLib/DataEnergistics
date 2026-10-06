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
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.jspecify.annotations.Nullable;

/** Resolves ExtendedAE's native crystal-assembler recipes for the asynchronous factory. */
public final class ExtendedAeReassemblerRecipeResolver {

    private ExtendedAeReassemblerRecipeResolver() {}

    /** Returns the first native ExtendedAE recipe that matches the supplied factory inputs. */
    public static @Nullable RecipeHolder<DataRipperReassemblerRecipe> findRecipe(
                                                                                 RecipeManager recipeManager,
                                                                                 DataRipperReassemblerRecipeInput input,
                                                                                 Level level,
                                                                                 ObjectSet<ResourceLocation> excludedRecipeIds) {
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
                                                                               ResourceLocation recipeId,
                                                                               DataRipperReassemblerRecipeInput input,
                                                                               Level level) {
        for (RecipeHolder<CrystalAssemblerRecipe> holder : recipeManager.getAllRecipesFor(CrystalAssemblerRecipe.TYPE)) {
            for (RecipeHolder<DataRipperReassemblerRecipe> adapted : adapt(holder)) {
                if (adapted.id().equals(recipeId) && adapted.value().matches(input, level)) {
                    return adapted;
                }
            }
        }
        return null;
    }

    private static ObjectList<RecipeHolder<DataRipperReassemblerRecipe>> adapt(RecipeHolder<CrystalAssemblerRecipe> holder) {
        try {
            CrystalAssemblerRecipe source = holder.value();
            ItemStack output = source.output;
            if (output.isEmpty()) {
                throw new IllegalArgumentException("Crystal assembler output must not be empty");
            }

            ObjectList<DataRipperReassemblerIngredient> itemInputs = new ObjectArrayList<>();
            for (IngredientStack.Item input : source.getInputs()) {
                if (!input.isEmpty()) {
                    itemInputs.add(new DataRipperReassemblerIngredient(input.getIngredient(), input.getAmount()));
                }
            }

            IngredientStack.@Nullable Fluid fluid = source.getFluid();
            ObjectList<ObjectList<GenericStack>> fluidVariants = createFluidVariants(fluid);
            ObjectList<RecipeHolder<DataRipperReassemblerRecipe>> variants = new ObjectArrayList<>(fluidVariants.size());
            for (ObjectList<GenericStack> fluidVariant : fluidVariants) {
                DataRipperReassemblerRecipe recipe = new DataRipperReassemblerRecipe(
                        itemInputs,
                        fluidVariant,
                        ObjectLists.singleton(new DataReassemblerItemOutput(output.copy(), null)),
                        ObjectLists.emptyList(),
                        DataRipperReassemblerRecipe.PROCESS_TICKS,
                        null,
                        null);
                variants.add(new RecipeHolder<>((holder.id()), recipe));
            }
            return ObjectLists.unmodifiable(variants);
        } catch (IllegalArgumentException exception) {
            Data_Energistics.LOGGER.warn(
                    "Skipped ExtendedAE crystal assembler recipe {} because it cannot be represented by the data reassembler: {}",
                    holder.id(), exception.getMessage());
            return ObjectLists.emptyList();
        }
    }

    private static ObjectList<ObjectList<GenericStack>> createFluidVariants(
                                                                            IngredientStack.@Nullable Fluid fluid) {
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
            throw new IllegalArgumentException("Crystal assembler fluid ingredient has no registered fluids");
        }
        return ObjectLists.unmodifiable(variants);
    }
}
