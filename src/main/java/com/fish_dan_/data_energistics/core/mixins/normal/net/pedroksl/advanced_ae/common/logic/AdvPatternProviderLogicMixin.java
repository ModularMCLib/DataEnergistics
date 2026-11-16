package com.fish_dan_.data_energistics.core.mixins.normal.net.pedroksl.advanced_ae.common.logic;

import com.fish_dan_.data_energistics.api.registry.provider.dispatch.PatternProviderDispatchHooks;
import com.fish_dan_.data_energistics.common.entrypoint.provider.PatternProviderRuntimeBindings;
import com.fish_dan_.data_energistics.integration.ae.advancedae.patternprovider.AdvancedAeDispatchAccess;
import com.fish_dan_.data_energistics.integration.ae.ae2.accessor.patternprovider.PatternProviderDispatchAccess;
import com.fish_dan_.data_energistics.integration.ae.ae2.accessor.patternprovider.PatternProviderLogicAccessor;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;

import net.pedroksl.advanced_ae.common.logic.AdvPatternProviderLogic;
import net.pedroksl.advanced_ae.common.logic.AdvPatternProviderLogicHost;

import me.fallenbreath.conditionalmixin.api.annotation.Condition;
import me.fallenbreath.conditionalmixin.api.annotation.Restriction;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Restriction(require = @Condition("advanced_ae"))
@Mixin(AdvPatternProviderLogic.class)
@NullMarked
public abstract class AdvPatternProviderLogicMixin implements PatternProviderLogicAccessor, PatternProviderDispatchAccess, AdvancedAeDispatchAccess {

    @Final
    @Shadow
    private List<GenericStack> sendList;

    @Unique
    private @Nullable PatternProviderDispatchHooks dataEnergistics$dispatchHooks;

    @Inject(method = "<init>(Lappeng/api/networking/IManagedGridNode;Lnet/pedroksl/advanced_ae/common/logic/AdvPatternProviderLogicHost;I)V",
            at = @At("RETURN"))
    private void dataEnergistics$bindDispatchHooks(CallbackInfo ci) {
        dataEnergistics$dispatchHooks = PatternProviderRuntimeBindings.createDispatchHooks(this);
    }

    @Override
    public @Nullable PatternProviderDispatchHooks dataEnergistics$dispatchHooks() {
        return dataEnergistics$dispatchHooks;
    }

    @Override
    @Accessor("host")
    public abstract AdvPatternProviderLogicHost dataEnergistics$dispatchHost();

    @Override
    @Accessor("actionSource")
    public abstract IActionSource dataEnergistics$dispatchActionSource();

    @Override
    public boolean dataEnergistics$sendingInputs() {
        return !sendList.isEmpty();
    }

    @Inject(method = "pushPattern", at = @At("RETURN"))
    private void dataEnergistics$afterPushPattern(IPatternDetails patternDetails,
                                                  KeyCounter[] inputHolder,
                                                  CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && dataEnergistics$dispatchHooks != null) {
            dataEnergistics$dispatchHooks.patternPushed();
        }
    }

    @Inject(method = "updateRedstoneState", at = @At("HEAD"))
    private void dataEnergistics$handlePulseUnlock(CallbackInfo ci) {
        if (dataEnergistics$dispatchHooks != null) {
            dataEnergistics$dispatchHooks.redstoneInputChanged();
        }
    }

    @Inject(method = "doWork", at = @At("HEAD"))
    private void dataEnergistics$tickRedstoneEmitter(CallbackInfoReturnable<Boolean> cir) {
        if (dataEnergistics$dispatchHooks != null) {
            dataEnergistics$dispatchHooks.beforeWork();
        }
    }

    @Inject(method = "doWork", at = @At("TAIL"))
    private void dataEnergistics$finishDispatchPulse(CallbackInfoReturnable<Boolean> cir) {
        if (dataEnergistics$dispatchHooks != null) {
            dataEnergistics$dispatchHooks.afterWork();
        }
    }

    @Override
    public boolean dataEnergistics$forcePulseUnlock() {
        return dataEnergistics$dispatchHooks != null && dataEnergistics$dispatchHooks.forcePulseUnlock();
    }
}
