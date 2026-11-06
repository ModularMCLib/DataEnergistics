package com.fish_dan_.data_energistics.core.mixins.net.pedroksl.advanced_ae.common.logic;

import com.fish_dan_.data_energistics.core.mixin.condition.RequireMods;

import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.GenericStack;

import net.pedroksl.advanced_ae.common.logic.AdvPatternProviderLogic;

import org.jspecify.annotations.NullMarked;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@RequireMods("advanced_ae")
@Mixin(AdvPatternProviderLogic.class)
@NullMarked
public interface AdvancedAePatternProviderLogicFieldAccessor {

    @Accessor("actionSource")
    IActionSource dataEnergistics$getActionSource();

    @Accessor("sendList")
    List<GenericStack> dataEnergistics$getSendList();
}
