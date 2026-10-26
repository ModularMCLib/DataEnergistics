package com.fish_dan_.data_energistics.mixin.ae.advancedae;

import com.fish_dan_.data_energistics.client.screen.AePatternProviderSlotStylePatch;
import com.fish_dan_.data_energistics.client.screen.PatternProviderUpgradePanel;
import com.fish_dan_.data_energistics.client.screen.ScreenSlotStylePatch;
import com.fish_dan_.data_energistics.client.widget.PatternProviderRedstoneTuningButton;
import com.fish_dan_.data_energistics.integration.ae.ae2.accessor.patternprovider.PatternProviderMenuAccessor;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.style.ScreenStyle;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.pedroksl.advanced_ae.client.gui.AdvPatternProviderScreen;
import net.pedroksl.advanced_ae.gui.advpatternprovider.AdvPatternProviderMenu;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AdvPatternProviderScreen.class)
public abstract class AdvancedAePatternProviderScreenMixin extends AEBaseScreen<AdvPatternProviderMenu> {

    @Unique
    private static final ScreenSlotStylePatch DATA_ENERGISTICS_STYLE_PATCH = AePatternProviderSlotStylePatch.rightPanelVerticalUpgrade("advanced_ae:adv_pattern_provider");

    @Unique
    private PatternProviderRedstoneTuningButton dataEnergistics$redstoneTuningButton;

    protected AdvancedAePatternProviderScreenMixin(AdvPatternProviderMenu menu, Inventory playerInventory,
                                                   Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
    }

    @Override
    protected void init() {
        PatternProviderUpgradePanel.installIfMissing(this.widgets, this.menu);
        super.init();
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void dataEnergistics$addRedstoneTuningButton(AdvPatternProviderMenu menu, Inventory playerInventory,
                                                         Component title, ScreenStyle style, CallbackInfo ci) {
        DATA_ENERGISTICS_STYLE_PATCH.apply(style);
        this.dataEnergistics$redstoneTuningButton = new PatternProviderRedstoneTuningButton((PatternProviderMenuAccessor) menu);
        this.addToLeftToolbar(this.dataEnergistics$redstoneTuningButton);
    }

    @Inject(method = "updateBeforeRender", at = @At("RETURN"))
    private void dataEnergistics$syncRedstoneTuningButton(CallbackInfo ci) {
        if (this.dataEnergistics$redstoneTuningButton != null) {
            this.dataEnergistics$redstoneTuningButton.syncFromMenu();
        }
    }
}
