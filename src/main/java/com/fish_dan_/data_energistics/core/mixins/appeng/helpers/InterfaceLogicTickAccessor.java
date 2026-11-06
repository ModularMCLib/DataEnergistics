package com.fish_dan_.data_energistics.core.mixins.appeng.helpers;

import appeng.helpers.InterfaceLogic;

import org.jspecify.annotations.NullMarked;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(InterfaceLogic.class)
@NullMarked
public interface InterfaceLogicTickAccessor {

    @Invoker("updateStorage")
    boolean dataEnergistics$invokeUpdateStorage();

    @Invoker("hasWorkToDo")
    boolean dataEnergistics$invokeHasWorkToDo();
}
