package com.fish_dan_.data_energistics.mixin.client.crafting;

import com.fish_dan_.data_energistics.ae2.sanctum.DataSanctumLargeInterfaceHost;
import com.fish_dan_.data_energistics.menu.sanctum.SetStockAmountMenuAccess;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.Icon;
import appeng.client.gui.me.crafting.SetStockAmountScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.ToggleButton;
import appeng.menu.implementations.SetStockAmountMenu;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds the per-slot unlimited toggle to AE2's stock amount editor. */
@Mixin(SetStockAmountScreen.class)
public abstract class SetStockAmountScreenMixin extends AEBaseScreen<SetStockAmountMenu> {

    @Unique
    private ToggleButton dataEnergistics$unlimitedButton;
    @Unique
    private ToggleButton dataEnergistics$policyButton;

    protected SetStockAmountScreenMixin(SetStockAmountMenu menu, Inventory inventory, Component title, ScreenStyle style) {
        super(menu, inventory, title, style);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void dataEnergistics$addUnlimitedButton(SetStockAmountMenu menu, Inventory inventory, Component title,
                                                    ScreenStyle style, CallbackInfo ci) {
        if (!(menu instanceof SetStockAmountMenuAccess access) || !(menu.getHost() instanceof DataSanctumLargeInterfaceHost)) {
            return;
        }
        dataEnergistics$unlimitedButton = new ToggleButton(
                Icon.FILTER_ON_EXTRACT_ENABLED,
                Icon.FILTER_ON_EXTRACT_DISABLED,
                Component.translatable("gui.data_energistics.data_sanctum_interface.unlimited_pull.enabled"),
                Component.translatable("gui.data_energistics.data_sanctum_interface.unlimited_pull.disabled"),
                ignored -> access.dataEnergistics$setUnlimited(!access.dataEnergistics$isUnlimited()));
        dataEnergistics$unlimitedButton.setState(access.dataEnergistics$isUnlimited());
        addToLeftToolbar(dataEnergistics$unlimitedButton);
        dataEnergistics$policyButton = new ToggleButton(
                Icon.PRIORITY,
                Icon.SCHEDULING_ROUND_ROBIN,
                Component.translatable("button.data_energistics.data_sanctum_interface.connector_policy.priority"),
                Component.translatable("button.data_energistics.data_sanctum_interface.connector_policy.round_robin"),
                ignored -> access.dataEnergistics$setPolicy(access.dataEnergistics$getPolicy() == 0 ? 1 : 0));
        dataEnergistics$policyButton.setState(access.dataEnergistics$getPolicy() == 1);
        addToLeftToolbar(dataEnergistics$policyButton);
    }

    @Inject(method = "updateBeforeRender", at = @At("TAIL"))
    private void dataEnergistics$syncUnlimitedButton(CallbackInfo ci) {
        if (dataEnergistics$unlimitedButton != null && getMenu() instanceof SetStockAmountMenuAccess access) {
            dataEnergistics$unlimitedButton.setState(access.dataEnergistics$isUnlimited());
            dataEnergistics$policyButton.setState(access.dataEnergistics$getPolicy() == 1);
        }
    }
}
