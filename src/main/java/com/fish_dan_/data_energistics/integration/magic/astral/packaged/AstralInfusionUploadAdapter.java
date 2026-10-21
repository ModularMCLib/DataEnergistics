package com.fish_dan_.data_energistics.integration.magic.astral.packaged;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.registry.machine.upload.PatternUploadWorkstationCompatibility;
import com.fish_dan_.data_energistics.api.registry.machine.upload.PatternUploadWorkstationContext;
import com.fish_dan_.data_energistics.api.registry.machine.upload.PatternUploadWorkstationInspection;
import com.fish_dan_.data_energistics.api.registry.machine.upload.PatternUploadWorkstationInspectionContext;
import com.fish_dan_.data_energistics.api.registry.machine.upload.PatternUploadWorkstationPreparation;
import com.fish_dan_.data_energistics.api.registry.machine.upload.PatternUploadWorkstationVariant;
import com.fish_dan_.data_energistics.common.data.DEDataComponents;

import appeng.api.crafting.IPatternDetails;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

import hellfirepvp.astralsorcery.common.tile.TileInfuser;
import org.jspecify.annotations.Nullable;

/** Classifies the installed liquid without reserving or consuming any native infusion source. */
final class AstralInfusionUploadAdapter {

    private AstralInfusionUploadAdapter() {}

    static PatternUploadWorkstationInspection inspect(PatternUploadWorkstationInspectionContext context) {
        if (context.recipeTypeId() != null && !AstralInfusionAdapter.RECIPE_TYPE.equals(context.recipeTypeId())) {
            return PatternUploadWorkstationInspection.pass();
        }
        var compatibility = context.patternDetails() == null || context.recipeId() == null ?
                PatternUploadWorkstationCompatibility.UNKNOWN :
                compatible(context.level(), context.workstationPosition(), context.patternDetails(), context.recipeTypeId(), context.recipeId()) ?
                        PatternUploadWorkstationCompatibility.COMPATIBLE : PatternUploadWorkstationCompatibility.INCOMPATIBLE;
        Fluid fluid = installedFluid(context.level(), context.workstationPosition());
        ResourceLocation fluidId = BuiltInRegistries.FLUID.getKey(fluid);
        Component name = Component.translatable("block.astralsorcery.infuser");
        if (fluid != Fluids.EMPTY) name = name.copy().append(" (").append(fluid.getFluidType().getDescription()).append(")");
        var variant = new PatternUploadWorkstationVariant(
                Data_Energistics.id("astral_sorcery_infusion/" + fluidId.getNamespace() + "/" + fluidId.getPath()), name);
        return PatternUploadWorkstationInspection.variant(variant, compatibility);
    }

    static PatternUploadWorkstationPreparation prepare(PatternUploadWorkstationContext context) {
        if (!AstralInfusionAdapter.RECIPE_TYPE.equals(context.recipeTypeId())) return PatternUploadWorkstationPreparation.pass();
        return compatible(context.level(), context.workstationPosition(), context.patternDetails(), context.recipeTypeId(), context.recipeId()) ?
                PatternUploadWorkstationPreparation.accepted() :
                PatternUploadWorkstationPreparation.rejected(Component.translatable("message.data_energistics.pattern_provider.target_unavailable"));
    }

    private static boolean compatible(ServerLevel level, BlockPos position, IPatternDetails pattern,
                                      @Nullable ResourceLocation type, @Nullable ResourceLocation id) {
        if (!AstralInfusionAdapter.RECIPE_TYPE.equals(type) || id == null ||
                !type.equals(pattern.getDefinition().get(DEDataComponents.PROCESSING_PATTERN_RECIPE_TYPE.get())) ||
                !id.equals(pattern.getDefinition().get(DEDataComponents.PROCESSING_PATTERN_RECIPE_ID.get())))
            return false;
        var recipe = AstralInfusionAdapter.recipe(level, id);
        return recipe != null && AstralInfusionPatternEncoding.matches(recipe.value(), pattern) &&
                AstralInfusionAdapter.fluidLayoutMatches(level, position, recipe.value());
    }

    private static Fluid installedFluid(ServerLevel level, BlockPos position) {
        Fluid installed = Fluids.EMPTY;
        for (BlockPos offset : TileInfuser.getLiquidOffsets()) {
            BlockPos fluidPosition = position.offset(offset);
            if (!level.isLoaded(fluidPosition)) return Fluids.EMPTY;
            Fluid fluid = level.getFluidState(fluidPosition).getType();
            if (fluid == Fluids.EMPTY || installed != Fluids.EMPTY && installed != fluid) return Fluids.EMPTY;
            installed = fluid;
        }
        return installed;
    }
}
