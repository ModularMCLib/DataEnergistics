package com.fish_dan_.data_energistics.client.terminal;

import com.fish_dan_.data_energistics.config.client.ClientConfigurations;
import com.fish_dan_.data_energistics.config.client.terminal.TerminalPreferences;
import com.fish_dan_.data_energistics.config.client.terminal.TerminalPreferences.UploadPanel;
import com.fish_dan_.data_energistics.config.client.terminal.TerminalPreferences.Window;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.jspecify.annotations.NullMarked;

/** Editable state for one terminal view; file snapshots and persistence are shared by the client configuration. */
@NullMarked
public final class TerminalClientPreferences {

    private final String screenId;
    public float xPercent;
    public float yPercent;
    public int width;
    public int height;
    public boolean folded;
    public boolean open;
    public UploadPanel uploadPanel;
    public boolean shiftClickToNetwork;
    public String sort;
    public String filter;
    public boolean descending;
    public final ObjectOpenHashSet<String> disabledKeyTypes;

    public TerminalClientPreferences(String screenId) {
        this.screenId = screenId;
        TerminalPreferences saved = ClientConfigurations.PLAYER_TERMINAL.get();
        Window window = saved.screens().getOrDefault(screenId, Window.DEFAULT);
        this.xPercent = window.xPercent();
        this.yPercent = window.yPercent();
        this.width = window.width();
        this.height = window.height();
        this.folded = window.folded();
        this.open = window.open();
        this.uploadPanel = window.upload();
        this.shiftClickToNetwork = saved.shiftClickToNetwork();
        this.sort = saved.sort();
        this.filter = saved.filter();
        this.descending = saved.descending();
        this.disabledKeyTypes = new ObjectOpenHashSet<>(saved.disabledKeyTypes());
    }

    public void save() {
        var screens = new Object2ObjectLinkedOpenHashMap<>(ClientConfigurations.PLAYER_TERMINAL.get().screens());
        screens.put(this.screenId, new Window(xPercent, yPercent, width, height, folded, open, uploadPanel));
        ClientConfigurations.PLAYER_TERMINAL.save(new TerminalPreferences(screens, shiftClickToNetwork,
                sort, filter, descending, disabledKeyTypes));
    }
}
