package com.fish_dan_.data_energistics.core.mixins.normal.appeng.menu.implementations;

import com.fish_dan_.data_energistics.common.menu.patternprovider.registration.PatternProviderMenuAccess;

import appeng.menu.AEBaseMenu;
import appeng.menu.implementations.MenuTypeBuilder;

import org.jspecify.annotations.NullMarked;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Runs registrations after the complete native menu constructor, on both logical sides. */
@Mixin(MenuTypeBuilder.class)
@NullMarked
public abstract class MenuTypeBuilderMixin<M extends AEBaseMenu> {

    @Shadow
    @Final
    @Mutable
    private MenuTypeBuilder.MenuFactory<M, Object> factory;

    @Inject(method = {
            "<init>(Ljava/lang/Class;Lappeng/menu/implementations/MenuTypeBuilder$MenuFactory;)V",
            "<init>(Ljava/lang/Class;Lappeng/menu/implementations/MenuTypeBuilder$TypedMenuFactory;)V"
    }, at = @At("RETURN"))
    private void dataEnergistics$extendMenuFactory(CallbackInfo ci) {
        var nativeFactory = factory;
        factory = (containerId, inventory, host) -> {
            M menu = nativeFactory.create(containerId, inventory, host);
            ((PatternProviderMenuAccess) menu).dataEnergistics$initializeProviderMenu();
            return menu;
        };
    }
}
