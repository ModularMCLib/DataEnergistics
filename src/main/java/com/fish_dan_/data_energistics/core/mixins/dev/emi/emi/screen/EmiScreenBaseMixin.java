package com.fish_dan_.data_energistics.core.mixins.dev.emi.emi.screen;

import com.fish_dan_.data_energistics.client.screen.research.ResearchStationScreen;

import net.minecraft.client.gui.screens.Screen;

import dev.emi.emi.screen.EmiScreenBase;
import me.fallenbreath.conditionalmixin.api.annotation.Condition;
import me.fallenbreath.conditionalmixin.api.annotation.Restriction;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Makes the full-screen research workspace invisible to EMI's automatic container integration. */
@Restriction(require = @Condition("emi"))
@Mixin(value = EmiScreenBase.class, remap = false)
@NullMarked
public abstract class EmiScreenBaseMixin {

    @Shadow
    @Final
    private static EmiScreenBase EMPTY;

    @Inject(method = "of", at = @At("HEAD"), cancellable = true, remap = false)
    private static void dataEnergistics$excludeResearchStation(@Nullable Screen screen, CallbackInfoReturnable<EmiScreenBase> callback) {
        if (screen instanceof ResearchStationScreen) callback.setReturnValue(EMPTY);
    }
}
