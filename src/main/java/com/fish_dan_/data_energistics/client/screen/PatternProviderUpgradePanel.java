package com.fish_dan_.data_energistics.client.screen;

import appeng.api.upgrades.Upgrades;
import appeng.client.gui.WidgetContainer;
import appeng.client.gui.widgets.UpgradesPanel;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantics;

/** Installs the native upgrade panel after provider screens and their addons have finished constructing widgets. */
public final class PatternProviderUpgradePanel {

    private static final String WIDGET_ID = "upgrades";

    private PatternProviderUpgradePanel() {}

    /**
     * Adds the panel on the client screen initialization thread when the owning screen has not supplied one.
     * Existing addon widgets keep ownership of their layout, and repeated screen initialization reuses the panel.
     *
     * @param widgets widget container of the fully constructed screen
     * @param menu provider menu whose upgrade slots the panel positions and renders
     */
    public static void installIfMissing(WidgetContainer widgets, AEBaseMenu menu) {
        if (widgets.widgets.containsKey(WIDGET_ID) || widgets.compositeWidgets.containsKey(WIDGET_ID)) {
            return;
        }

        var slots = menu.getSlots(SlotSemantics.UPGRADE);
        if (slots.isEmpty()) {
            return;
        }

        if (menu.getTarget() instanceof PatternProviderLogicHost host) {
            widgets.add(WIDGET_ID, new UpgradesPanel(slots,
                    () -> Upgrades.getTooltipLinesForMachine(host.getTerminalIcon().getItem())));
        } else {
            widgets.add(WIDGET_ID, new UpgradesPanel(slots));
        }
    }
}
