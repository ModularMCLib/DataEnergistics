package com.fish_dan_.data_energistics.client.terminal.search;

import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;

import appeng.core.AEConfig;
import appeng.integration.abstraction.ItemListMod;

import org.jspecify.annotations.NullMarked;

/** Client-thread search lifecycle using AE's shared settings and active recipe-viewer adapter. */
@NullMarked
public final class TerminalSearchState {

    private static String rememberedSearch = "";
    private final TextField field;
    private final Runnable changed;
    private String query = "";
    private boolean opened;

    public TerminalSearchState(TextField field, Runnable changed) {
        this.field = field;
        this.changed = changed;
        field.setTextResponder(this::edited);
    }

    /** Restores search once per overlay; expanding the collapsed entry can focus it again. */
    public void open(boolean visible) {
        if (!visible) return;
        if (!opened) {
            opened = true;
            if (AEConfig.instance().isRememberLastSearch()) {
                field.setText(rememberedSearch, false);
                field.setCursor(rememberedSearch.length());
                field.setSelection(0, rememberedSearch.length());
            }
            if (external() && AEConfig.instance().isClearExternalSearchOnOpen()) ItemListMod.setSearchText("");
        }
        tick();
        focusOnOpen();
    }

    private void focusOnOpen() {
        if (!external() && AEConfig.instance().isAutoFocusSearch()) field.focus();
    }

    public String query() {
        return query;
    }

    public void tick() {
        boolean external = external();
        if (field.isVisible() == external) {
            if (external) field.blur();
            field.setVisible(!external);
        }
        if (external) {
            update(ItemListMod.getSearchText());
            return;
        }
        if (ItemListMod.isEnabled() && AEConfig.instance().isSyncWithExternalSearch()) {
            if (field.isFocused()) {
                if (!field.getValue().equals(ItemListMod.getSearchText())) ItemListMod.setSearchText(field.getValue());
            } else if (ItemListMod.hasSearchFocus()) {
                String text = ItemListMod.getSearchText();
                if (!field.getValue().equals(text)) field.setText(text, false);
            }
        }
        update(field.getValue());
    }

    public void remember() {
        if (opened) rememberedSearch = field.getValue();
    }

    private void edited(String text) {
        if (external()) return;
        if (ItemListMod.isEnabled() && AEConfig.instance().isSyncWithExternalSearch()) ItemListMod.setSearchText(text);
        update(text);
    }

    private void update(String text) {
        if (query.equals(text)) return;
        query = text;
        changed.run();
    }

    private static boolean external() {
        return ItemListMod.isEnabled() && AEConfig.instance().isUseExternalSearch();
    }
}
