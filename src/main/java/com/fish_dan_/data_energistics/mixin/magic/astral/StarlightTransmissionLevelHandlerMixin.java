package com.fish_dan_.data_energistics.mixin.magic.astral;

import com.fish_dan_.data_energistics.integration.magic.astral.AstralSorceryDigitalSupplyAdapter;

import net.minecraft.server.level.ServerLevel;

import hellfirepvp.astralsorcery.common.starlight.transmission.StarlightTransmissionLevelHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Runs explicitly linked Digital Supply Interface sources after Astral's native source pass. */
@Mixin(value = StarlightTransmissionLevelHandler.class, remap = false)
public abstract class StarlightTransmissionLevelHandlerMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void dataEnergistics$transmitDigitalSupply(ServerLevel level, CallbackInfo callback) {
        AstralSorceryDigitalSupplyAdapter.transmitRegisteredInterfaces(level);
    }
}
