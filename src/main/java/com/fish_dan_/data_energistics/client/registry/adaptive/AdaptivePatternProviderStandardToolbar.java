package com.fish_dan_.data_energistics.client.registry.adaptive;

import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsEntrypoint;
import com.fish_dan_.data_energistics.api.entrypoint.client.DataEnergisticsClientPlugin;
import com.fish_dan_.data_energistics.api.entrypoint.client.DataEnergisticsClientRegistry;
import com.fish_dan_.data_energistics.api.registry.adaptive.AdaptivePatternProviderToolbarActions;
import com.fish_dan_.data_energistics.api.registry.adaptive.client.AdaptivePatternProviderToolbarButton;
import com.fish_dan_.data_energistics.api.registry.adaptive.client.AdaptivePatternProviderToolbarContext;
import com.fish_dan_.data_energistics.client.widget.PatternProviderRedstoneTuningButton;

import appeng.api.config.LockCraftingMode;
import appeng.api.config.Settings;
import appeng.api.config.YesNo;
import appeng.client.gui.Icon;
import appeng.client.gui.widgets.ServerSettingToggleButton;
import appeng.client.gui.widgets.ToggleButton;
import appeng.core.localization.GuiText;
import appeng.core.network.serverbound.ConfigButtonPacket;

import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Registers AE2 settings and Adaptive paging/card controls through the client entrypoint contract.
 */
@DataEnergisticsEntrypoint(clientOnly = true)
public final class AdaptivePatternProviderStandardToolbar implements DataEnergisticsClientPlugin {

    @Override
    public void register(DataEnergisticsClientRegistry registry) {
        var toolbar = registry.adaptivePatternProviderToolbar();
        toolbar.register(AdaptivePatternProviderToolbarActions.BLOCKING_MODE, 100, context -> {
            var button = new ServerSettingToggleButton<>(Settings.BLOCKING_MODE, YesNo.NO);
            return new AdaptivePatternProviderToolbarButton(button, () -> button.set(context.menu().getBlockingMode()));
        });
        toolbar.register(AdaptivePatternProviderToolbarActions.LOCK_CRAFTING_MODE, 200, context -> {
            var button = new ServerSettingToggleButton<>(Settings.LOCK_CRAFTING_MODE, LockCraftingMode.NONE);
            return new AdaptivePatternProviderToolbarButton(button, () -> button.set(context.menu().getLockCraftingMode()));
        });
        toolbar.register(AdaptivePatternProviderToolbarActions.PATTERN_ACCESS_TERMINAL, 300, context -> {
            var button = new ToggleButton(Icon.PATTERN_ACCESS_SHOW, Icon.PATTERN_ACCESS_HIDE,
                    GuiText.PatternAccessTerminal.text(), GuiText.PatternAccessTerminalHint.text(),
                    ignored -> PacketDistributor.sendToServer(new ConfigButtonPacket(
                            Settings.PATTERN_ACCESS_TERMINAL, context.handlingRightClick().getAsBoolean())));
            return new AdaptivePatternProviderToolbarButton(button,
                    () -> button.setState(context.menu().getShowInAccessTerminal() == YesNo.YES));
        });
        toolbar.register(AdaptivePatternProviderToolbarActions.PREVIOUS_PAGE, 400, context -> pageButton(context, false));
        toolbar.register(AdaptivePatternProviderToolbarActions.NEXT_PAGE, 500, context -> pageButton(context, true));
        toolbar.register(AdaptivePatternProviderToolbarActions.REDSTONE_TUNING, 800, context -> {
            var menu = context.menu();
            var button = new PatternProviderRedstoneTuningButton(menu::hasRedstoneTuningCard,
                    menu::getRedstoneTuningMode, menu::setRedstoneTuningMode);
            return new AdaptivePatternProviderToolbarButton(button, button::syncFromMenu);
        });
    }

    private static AdaptivePatternProviderToolbarButton pageButton(AdaptivePatternProviderToolbarContext context,
                                                                   boolean next) {
        var menu = context.menu();
        Icon icon = next ? Icon.ARROW_RIGHT : Icon.BACK;
        Component text = Component.translatable(next ? "screen.data_energistics.page.next" : "screen.data_energistics.page.previous");
        var button = new ToggleButton(icon, icon, text, text,
                ignored -> menu.sendSetPage(menu.getPageIndex() + (next ? 1 : -1)));
        return new AdaptivePatternProviderToolbarButton(button, () -> {
            button.visible = menu.getTotalPages() > 1;
            button.active = next ? menu.getPageIndex() + 1 < menu.getTotalPages() : menu.getPageIndex() > 0;
        });
    }
}
