package com.fish_dan_.data_energistics.integration.crafting.packaged.botania;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.entity.BlockEntity;

import org.jspecify.annotations.Nullable;
import vazkii.botania.api.recipe.ElvenTradeRecipe;
import vazkii.botania.api.recipe.ManaInfusionRecipe;
import vazkii.botania.api.recipe.PetalApothecaryRecipe;
import vazkii.botania.api.recipe.RunicAltarRecipe;
import vazkii.botania.api.recipe.TerrestrialAgglomerationRecipe;
import vazkii.botania.common.block.block_entity.AlfheimPortalBlockEntity;
import vazkii.botania.common.block.block_entity.PetalApothecaryBlockEntity;
import vazkii.botania.common.block.block_entity.RunicAltarBlockEntity;
import vazkii.botania.common.block.block_entity.TerrestrialAgglomerationPlateBlockEntity;
import vazkii.botania.common.block.block_entity.mana.ManaPoolBlockEntity;

enum BotaniaMachineKind {

    PETAL("petal_apothecary"),
    MANA("mana_infusion"),
    PORTAL("elven_trade"),
    TERRA("terrestrial_agglomeration_plate"),
    RUNE("runic_altar");

    final ResourceLocation category;

    BotaniaMachineKind(String path) {
        this.category = ResourceLocation.fromNamespaceAndPath("botania", path);
    }

    boolean accepts(@Nullable BlockEntity tile) {
        return switch (this) {
            case PETAL -> tile instanceof PetalApothecaryBlockEntity;
            case MANA -> tile instanceof ManaPoolBlockEntity;
            case PORTAL -> tile instanceof AlfheimPortalBlockEntity;
            case TERRA -> tile instanceof TerrestrialAgglomerationPlateBlockEntity;
            case RUNE -> tile instanceof RunicAltarBlockEntity;
        };
    }

    boolean accepts(Recipe<?> recipe) {
        return switch (this) {
            case PETAL -> recipe instanceof PetalApothecaryRecipe;
            case MANA -> recipe instanceof ManaInfusionRecipe;
            case PORTAL -> recipe instanceof ElvenTradeRecipe;
            case TERRA -> recipe instanceof TerrestrialAgglomerationRecipe;
            case RUNE -> recipe instanceof RunicAltarRecipe;
        };
    }
}
