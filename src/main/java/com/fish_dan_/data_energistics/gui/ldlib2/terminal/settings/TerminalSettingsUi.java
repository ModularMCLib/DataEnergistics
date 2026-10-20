package com.fish_dan_.data_energistics.gui.ldlib2.terminal.settings;

import com.fish_dan_.data_energistics.gui.ldlib2.terminal.TerminalActionButton;
import com.fish_dan_.data_energistics.gui.ldlib2.terminal.TerminalUiLayout;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;

import appeng.core.AEConfig;
import appeng.core.localization.GuiText;
import appeng.integration.abstraction.ItemListMod;

import net.minecraft.network.chat.Component;

import org.jspecify.annotations.NullMarked;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** AE's shared terminal preferences presented in a draggable LDLib2 sub-window. */
@NullMarked
public final class TerminalSettingsUi {

    public static final int WIDTH = 200;
    public static final int HEIGHT = 186;
    private final TerminalActionButton pin;
    private final TerminalActionButton notify;
    private final TerminalActionButton internal;
    private final TerminalActionButton external;
    private final TerminalActionButton remember;
    private final TerminalActionButton autoFocus;
    private final TerminalActionButton sync;
    private final TerminalActionButton clearExternal;
    private final Runnable changed;

    public TerminalSettingsUi(UIElement root, Runnable changed) {
        this.changed = changed;
        var config = AEConfig.instance();
        Component externalMod = Component.literal(ItemListMod.isEnabled() ? ItemListMod.getShortName() : "JEI/EMI");
        TerminalUiLayout.require(root, "popup_title", Label.class).setText(GuiText.TerminalSettingsTitle.text());
        pin = toggle(root, "pin_crafted", GuiText.TerminalSettingsPinAutoCraftedItems.text(), 25, 24,
                config::isPinAutoCraftedItems, config::setPinAutoCraftedItems);
        notify = toggle(root, "notify_finished", GuiText.TerminalSettingsNotifyForFinishedJobs.text(), 50, 30,
                config::isNotifyForFinishedCraftingJobs, config::setNotifyForFinishedCraftingJobs);
        Label heading = TerminalUiLayout.at(new Label(), 8, 88, WIDTH - 16, 10);
        heading.addClass("terminal-popup-heading");
        heading.setText(GuiText.SearchSettingsTitle.text());
        heading.textStyle(style -> style.adaptiveWidth(false).adaptiveHeight(false));
        heading.setAllowHitTest(false);
        root.addChild(heading);
        internal = radio(root, "internal_search", GuiText.SearchSettingsUseInternalSearch.text(), 10, false);
        external = radio(root, "external_search", GuiText.SearchSettingsUseExternalSearch.text(externalMod), 95, true);
        remember = toggle(root, "remember_search", GuiText.SearchSettingsRememberSearch.text(), 126, 14,
                config::isRememberLastSearch, config::setRememberLastSearch);
        autoFocus = toggle(root, "auto_focus", GuiText.SearchSettingsAutoFocus.text(), 142, 14,
                config::isAutoFocusSearch, config::setAutoFocusSearch);
        sync = toggle(root, "sync_search", GuiText.SearchSettingsSyncWithExternal.text(externalMod), 158, 22,
                config::isSyncWithExternalSearch, config::setSyncWithExternalSearch);
        clearExternal = toggle(root, "clear_external", GuiText.SearchSettingsClearExternal.text(externalMod), 126, 24,
                config::isClearExternalSearchOnOpen, config::setClearExternalSearchOnOpen);
        refresh();
    }

    private TerminalActionButton toggle(UIElement root, String id, Component text, int top, int height,
                                        BooleanSupplier value, Consumer<Boolean> save) {
        TerminalActionButton button = TerminalUiLayout.toggle(id, text, 10, top, WIDTH - 20);
        wrap(button, id, height);
        button.setOnClick(event -> {
            if (event.button != 0) return;
            save.accept(!value.getAsBoolean());
            refresh();
            changed.run();
        });
        root.addChild(button);
        return button;
    }

    private TerminalActionButton radio(UIElement root, String id, Component text, int left, boolean externalSearch) {
        TerminalActionButton button = TerminalUiLayout.toggle(id, text, left, 102, 85);
        wrap(button, id, 14);
        button.addClass("terminal-search-radio");
        TerminalUiLayout.require(button, id + "_icon", UIElement.class).layout(layout -> layout.width(14).height(14));
        TerminalUiLayout.require(button, id + "_label", Label.class).layout(layout -> layout.paddingLeft(16));
        button.setOnClick(event -> {
            if (event.button != 0) return;
            AEConfig.instance().setUseExternalSearch(externalSearch);
            refresh();
            changed.run();
        });
        root.addChild(button);
        return button;
    }

    private static void wrap(TerminalActionButton button, String id, int height) {
        button.addClass("terminal-settings-option");
        button.layout(layout -> layout.height(height));
        Label label = TerminalUiLayout.require(button, id + "_label", Label.class);
        label.layout(layout -> layout.top(2).height(height - 2));
        label.textStyle(style -> style.textWrap(TextWrap.WRAP));
    }

    public void refresh() {
        var config = AEConfig.instance();
        boolean available = ItemListMod.isEnabled();
        boolean useExternal = available && config.isUseExternalSearch();
        selected(pin, config.isPinAutoCraftedItems());
        selected(notify, config.isNotifyForFinishedCraftingJobs());
        selected(internal, !useExternal);
        selected(external, useExternal);
        selected(remember, config.isRememberLastSearch());
        selected(autoFocus, config.isAutoFocusSearch());
        selected(sync, config.isSyncWithExternalSearch());
        selected(clearExternal, config.isClearExternalSearchOnOpen());
        external.setActive(available);
        sync.setActive(available);
        remember.setVisible(!useExternal);
        autoFocus.setVisible(!useExternal);
        sync.setVisible(!useExternal);
        clearExternal.setVisible(useExternal);
    }

    private static void selected(UIElement element, boolean value) {
        if (value) element.addClass("selected");
        else element.removeClass("selected");
    }
}
