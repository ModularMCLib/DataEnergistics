package com.fish_dan_.data_energistics.core.mixins.ae.advancedae;

import com.fish_dan_.data_energistics.core.mixin.condition.RequireMods;

import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.GenericStack;

import net.pedroksl.advanced_ae.common.logic.AdvPatternProviderLogic;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@RequireMods("advanced_ae")
@Mixin(AdvPatternProviderLogic.class)
public interface AdvancedAePatternProviderLogicFieldAccessor {

    @Accessor("actionSource")
    IActionSource dataEnergistics$getActionSource();

    @Accessor("sendList")
    List<GenericStack> dataEnergistics$getSendList();
}
