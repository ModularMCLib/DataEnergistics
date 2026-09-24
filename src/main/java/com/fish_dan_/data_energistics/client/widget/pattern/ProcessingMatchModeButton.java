package com.fish_dan_.data_energistics.client.widget.pattern;

import com.fish_dan_.data_energistics.api.crafting.matching.ProcessingMatchMode;

import appeng.client.gui.Icon;
import appeng.client.gui.widgets.IconButton;

import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Three-state item matching control shared by every processing amount slot. */
public final class ProcessingMatchModeButton extends IconButton {

    private final Supplier<ProcessingMatchMode> mode;

    public ProcessingMatchModeButton(Supplier<ProcessingMatchMode> mode, Consumer<ProcessingMatchMode> setMode) {
        super(button -> setMode.accept(mode.get().next()));
        this.mode = mode;
    }

    @Override
    protected Icon getIcon() {
        return switch (mode.get()) {
            case EXACT -> Icon.FUZZY_IGNORE;
            case ID -> Icon.FUZZY_PERCENT_99;
            case TAG -> Icon.FUZZY_PERCENT_75;
        };
    }

    @Override
    public List<Component> getTooltipMessage() {
        return List.of(Component.translatable("gui.data_energistics.processing_match." + mode.get().name().toLowerCase(Locale.ROOT)),
                Component.translatable("gui.data_energistics.processing_match.hint"));
    }
}
