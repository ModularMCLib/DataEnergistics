package com.fish_dan_.data_energistics.core.mixins.normal.io.github.lounode.ae2cs.common.me.logic;

import com.fish_dan_.data_energistics.integration.ae.ae2.accessor.patternprovider.PatternProviderDispatchAccess;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.KeyCounter;

import io.github.lounode.ae2cs.common.me.logic.MeteoritePatternProviderLogic;
import io.github.lounode.ae2cs.common.me.logic.ResonatingPatternProviderLogic;
import me.fallenbreath.conditionalmixin.api.annotation.Condition;
import me.fallenbreath.conditionalmixin.api.annotation.Restriction;
import org.jspecify.annotations.NullMarked;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Restriction(require = @Condition("ae2cs"))
@Mixin({ ResonatingPatternProviderLogic.class, MeteoritePatternProviderLogic.class })
@NullMarked
public abstract class Ae2CsPatternProviderLogicMixin {

    @Inject(method = "pushPattern", at = @At("HEAD"))
    private void dataEnergistics$scheduleAutoRequestPulse(IPatternDetails patternDetails,
                                                          KeyCounter[] inputHolder,
                                                          CallbackInfoReturnable<Boolean> cir) {
        var hooks = ((PatternProviderDispatchAccess) this).dataEnergistics$dispatchHooks();
        if (hooks != null) {
            hooks.redstoneInputChanged();
        }
    }

    @Inject(method = "pushPattern", at = @At("RETURN"))
    private void dataEnergistics$afterPushPattern(IPatternDetails patternDetails,
                                                  KeyCounter[] inputHolder,
                                                  CallbackInfoReturnable<Boolean> cir) {
        var hooks = ((PatternProviderDispatchAccess) this).dataEnergistics$dispatchHooks();
        if (cir.getReturnValueZ() && hooks != null) {
            hooks.patternPushed();
        }
    }
}
