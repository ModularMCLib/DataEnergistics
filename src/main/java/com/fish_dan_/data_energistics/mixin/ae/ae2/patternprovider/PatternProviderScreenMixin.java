package com.fish_dan_.data_energistics.mixin.ae.ae2.patternprovider;

import com.fish_dan_.data_energistics.client.screen.AePatternProviderSlotStylePatch;
import com.fish_dan_.data_energistics.client.screen.PatternProviderUpgradePanel;
import com.fish_dan_.data_energistics.client.screen.ScreenSlotStylePatch;
import com.fish_dan_.data_energistics.client.widget.PatternProviderRedstoneTuningButton;
import com.fish_dan_.data_energistics.integration.ae.ae2.accessor.patternprovider.PatternProviderMenuAccessor;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.implementations.PatternProviderScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.menu.implementations.PatternProviderMenu;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PatternProviderScreen.class)
public abstract class PatternProviderScreenMixin extends AEBaseScreen<PatternProviderMenu> {

    @Unique
    private static final ScreenSlotStylePatch DATA_ENERGISTICS_STYLE_PATCH = AePatternProviderSlotStylePatch.inlineSingleUpgrade("ae2:pattern_provider");

    @Unique
    private PatternProviderRedstoneTuningButton dataEnergistics$redstoneTuningButton;

    protected PatternProviderScreenMixin(PatternProviderMenu menu, Inventory playerInventory, Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
    }

    @Override
    protected void init() {
        PatternProviderUpgradePanel.installIfMissing(this.widgets, this.menu);
        super.init();
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void dataEnergistics$addButton(PatternProviderMenu menu, Inventory playerInventory, Component title,
                                           ScreenStyle style, CallbackInfo ci) {
        DATA_ENERGISTICS_STYLE_PATCH.apply(style);
        this.dataEnergistics$redstoneTuningButton = new PatternProviderRedstoneTuningButton((PatternProviderMenuAccessor) menu);
        this.addToLeftToolbar(this.dataEnergistics$redstoneTuningButton);
    }

    @Inject(method = "updateBeforeRender", at = @At("RETURN"))
    private void dataEnergistics$syncButton(CallbackInfo ci) {
        if (this.dataEnergistics$redstoneTuningButton != null) {
            this.dataEnergistics$redstoneTuningButton.syncFromMenu();
        }
    }
}
