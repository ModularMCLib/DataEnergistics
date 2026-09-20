package com.fish_dan_.data_energistics.client.screen.machine;

import com.fish_dan_.data_energistics.menu.patternprovider.DigitalPackagedPatternProviderMenu;

import appeng.client.gui.implementations.PatternProviderScreen;
import appeng.client.gui.style.ScreenStyle;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Displays the standalone packaged provider without adaptive or remote controls. */
public final class DigitalPackagedPatternProviderScreen
                                                        extends PatternProviderScreen<DigitalPackagedPatternProviderMenu> {

    public DigitalPackagedPatternProviderScreen(DigitalPackagedPatternProviderMenu menu,
                                                Inventory playerInventory,
                                                Component title,
                                                ScreenStyle style) {
        super(menu, playerInventory, title, style);
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        setTextContent(
                "operation_status",
                Component.translatable(
                        "screen.data_energistics.digital_packaged_pattern_provider.operations",
                        this.menu.pendingOperations));
    }
}
