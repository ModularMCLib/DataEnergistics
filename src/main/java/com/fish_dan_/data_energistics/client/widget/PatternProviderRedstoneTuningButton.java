package com.fish_dan_.data_energistics.client.widget;

import com.fish_dan_.data_energistics.accessor.patternprovider.PatternProviderMenuAccessor;
import com.fish_dan_.data_energistics.ae2.patternprovider.RedstoneTuningMode;

import appeng.client.gui.Icon;

import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

public class PatternProviderRedstoneTuningButton extends DataExtractorToggleButton {

    private final BooleanSupplier hasCard;
    private final IntSupplier getMode;
    private final IntConsumer setMode;

    public PatternProviderRedstoneTuningButton(PatternProviderMenuAccessor menu) {
        this(menu::dataEnergistics$hasRedstoneTuningCard, menu::dataEnergistics$getRedstoneTuningMode,
                menu::dataEnergistics$setRedstoneTuningMode);
    }

    public PatternProviderRedstoneTuningButton(BooleanSupplier hasCard, IntSupplier getMode, IntConsumer setMode) {
        super(
                Icon.REDSTONE_ON,
                Icon.REDSTONE_OFF,
                "button.data_energistics.pattern_provider.redstone_tuning",
                "button.data_energistics.pattern_provider.redstone_tuning.pulse_to_unlock_once",
                "button.data_energistics.pattern_provider.redstone_tuning.emit_on_dispatch",
                ignored -> {});
        this.hasCard = hasCard;
        this.getMode = getMode;
        this.setMode = setMode;
    }

    @Override
    public void onPress() {
        var mode = RedstoneTuningMode.values()[this.getMode.getAsInt()].next();
        this.setMode.accept(mode.ordinal());
        syncFromMenu();
    }

    public void syncFromMenu() {
        setState(RedstoneTuningMode.values()[this.getMode.getAsInt()] == RedstoneTuningMode.PULSE_TO_UNLOCK_ONCE);
        this.visible = this.hasCard.getAsBoolean();
        this.active = this.visible;
    }
}
