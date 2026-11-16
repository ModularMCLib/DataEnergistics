package com.fish_dan_.data_energistics.client.screen.patternprovider;

import com.fish_dan_.data_energistics.api.registry.provider.client.PatternProviderScreenContext;
import com.fish_dan_.data_energistics.client.screen.PatternProviderUpgradePanel;
import com.fish_dan_.data_energistics.client.screen.ScreenSlotStylePatch;
import com.fish_dan_.data_energistics.client.widget.PatternProviderRedstoneTuningButton;
import com.fish_dan_.data_energistics.common.menu.patternprovider.registration.PatternProviderMenuAccess;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** Shared redstone button and upgrade panel for registered native provider screens. */
@NullMarked
public final class RedstoneTuningScreenBinding {

    private RedstoneTuningScreenBinding() {}

    /** Creates the widgets once before layout, or declines hosts without a registered redstone control. */
    public static @Nullable Runnable create(PatternProviderScreenContext context, ScreenSlotStylePatch slotStyle) {
        var screen = context.screen();
        var redstoneMenu = ((PatternProviderMenuAccess) screen.getMenu()).dataEnergistics$providerRedstoneMenu();
        if (redstoneMenu == null) {
            return null;
        }
        slotStyle.apply(screen.getStyle());
        PatternProviderUpgradePanel.installIfMissing(context.widgets(), screen.getMenu());
        var button = new PatternProviderRedstoneTuningButton(redstoneMenu);
        context.addToolbarButton().accept(button);
        return button::syncFromMenu;
    }
}
