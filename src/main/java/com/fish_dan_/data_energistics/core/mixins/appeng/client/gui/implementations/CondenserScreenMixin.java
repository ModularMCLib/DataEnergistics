package com.fish_dan_.data_energistics.core.mixins.appeng.client.gui.implementations;

import com.fish_dan_.data_energistics.client.widget.CondenserOutputModeButton;
import com.fish_dan_.data_energistics.integration.ae.ae2.accessor.condenser.CondenserMenuAccessor;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.WidgetContainer;
import appeng.client.gui.implementations.CondenserScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.menu.implementations.CondenserMenu;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.jspecify.annotations.NullMarked;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CondenserScreen.class)
@NullMarked
public abstract class CondenserScreenMixin extends AEBaseScreen<CondenserMenu> {

    @Unique
    private CondenserOutputModeButton dataEnergistics$condenserModeButton;

    protected CondenserScreenMixin(CondenserMenu menu, Inventory playerInventory, Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
    }

    @WrapOperation(method = "<init>",
                   at = @At(value = "INVOKE", target = "Lappeng/client/gui/WidgetContainer;add(Ljava/lang/String;Lnet/minecraft/client/gui/components/AbstractWidget;)V"))
    private void dataEnergistics$replaceModeButton(WidgetContainer widgets, String id, AbstractWidget widget,
                                                   Operation<Void> original) {
        if (id.equals("mode")) {
            this.dataEnergistics$condenserModeButton = new CondenserOutputModeButton((CondenserMenuAccessor) this.menu);
            widget = this.dataEnergistics$condenserModeButton;
        }
        original.call(widgets, id, widget);
    }

    @Inject(method = "updateBeforeRender", at = @At("RETURN"))
    private void dataEnergistics$syncModeButton(CallbackInfo ci) {
        if (this.dataEnergistics$condenserModeButton == null) {
            return;
        }

        this.dataEnergistics$condenserModeButton.setModeIndex(
                ((CondenserMenuAccessor) this.menu).dataEnergistics$getCondenserOutputMode());
    }
}
