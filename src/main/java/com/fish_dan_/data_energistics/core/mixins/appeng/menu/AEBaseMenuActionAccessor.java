package com.fish_dan_.data_energistics.core.mixins.appeng.menu;

import appeng.menu.AEBaseMenu;

import org.jspecify.annotations.NullMarked;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(AEBaseMenu.class)
@NullMarked
public interface AEBaseMenuActionAccessor {

    @Invoker("sendClientAction")
    void dataEnergistics$invokeSendClientAction(String action, Object arg);
}
