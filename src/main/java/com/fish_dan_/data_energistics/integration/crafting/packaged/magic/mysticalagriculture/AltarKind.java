package com.fish_dan_.data_energistics.integration.crafting.packaged.magic.mysticalagriculture;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.entity.BlockEntity;

import com.blakebr0.mysticalagriculture.api.crafting.IAwakeningRecipe;
import com.blakebr0.mysticalagriculture.api.crafting.IInfusionRecipe;
import com.blakebr0.mysticalagriculture.init.ModRecipeTypes;
import com.blakebr0.mysticalagriculture.tileentity.AwakeningAltarTileEntity;
import com.blakebr0.mysticalagriculture.tileentity.InfusionAltarTileEntity;
import org.jspecify.annotations.Nullable;

public enum AltarKind {

    INFUSION("infusion"),
    AWAKENING("awakening");

    final ResourceLocation recipeType;

    AltarKind(String path) {
        this.recipeType = ResourceLocation.fromNamespaceAndPath("mysticalagriculture", path);
    }

    boolean recognizes(@Nullable BlockEntity entity) {
        return this == INFUSION ? entity instanceof InfusionAltarTileEntity :
                entity instanceof AwakeningAltarTileEntity;
    }

    @Nullable
    Recipe<CraftingInput> recipe(Recipe<?> recipe) {
        if (this == INFUSION && recipe instanceof IInfusionRecipe infusion &&
                recipe.getType() == ModRecipeTypes.INFUSION.get())
            return infusion;
        if (this == AWAKENING && recipe instanceof IAwakeningRecipe awakening &&
                recipe.getType() == ModRecipeTypes.AWAKENING.get())
            return awakening;
        return null;
    }
}
