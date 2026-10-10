package com.fish_dan_.data_energistics.mixin.magic.astral;

import com.fish_dan_.data_energistics.integration.magic.astral.AstralLiquidSupplyTransaction;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;

import hellfirepvp.astralsorcery.common.lib.FluidsAS;
import hellfirepvp.astralsorcery.common.tile.TileChalice;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Adds AE-backed Liquid Starlight sources to Astral's native altar and infuser draw instance. */
@Mixin(value = TileChalice.LiquidDrawInstance.class, remap = false)
public abstract class AstralAltarLiquidDrawMixin {

    @Unique
    private final AstralLiquidSupplyTransaction dataEnergistics$liquidSupply = new AstralLiquidSupplyTransaction();

    @Inject(method = "update", at = @At("TAIL"))
    private void dataEnergistics$discoverDigitalSupply(Level level, BlockPos pos, FluidStack search, CallbackInfo callback) {
        this.dataEnergistics$liquidSupply.discover(level, pos, search);
    }

    @Inject(method = "consumeLiquid", at = @At("HEAD"), cancellable = true)
    private void dataEnergistics$consumeDigitalSupply(Level level, BlockPos pos, FluidStack search,
                                                      boolean simulate, CallbackInfoReturnable<Boolean> callback) {
        if (!dataEnergistics$isLiquidStarlight(search) || !this.dataEnergistics$liquidSupply.hasInterfaces()) {
            return;
        }
        callback.setReturnValue(this.dataEnergistics$liquidSupply.consume(level, pos, search, simulate));
        callback.cancel();
    }

    @Unique
    private static boolean dataEnergistics$isLiquidStarlight(FluidStack stack) {
        return !stack.isEmpty() && stack.getFluid() == FluidsAS.LIQUID_STARLIGHT.getSource().get();
    }
}
