package com.fish_dan_.data_energistics.config.client;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.CraftingQuantityMode;
import com.fish_dan_.data_energistics.config.DataEnergisticsConfiguration.BlankPatternSource;

import net.minecraft.network.chat.Component;

import dev.toma.configuration.client.WidgetAdder;
import dev.toma.configuration.client.screen.WidgetPlacerHelper;
import dev.toma.configuration.client.theme.ConfigTheme;
import dev.toma.configuration.client.theme.adapter.AbstractDisplayAdapter;
import dev.toma.configuration.client.widget.EnumWidget;
import dev.toma.configuration.config.ConfigHolder;
import dev.toma.configuration.config.value.ConfigValue;
import dev.toma.configuration.config.value.EnumValue;

import java.lang.reflect.Field;

/** Renders project configuration enums with localized labels. */
final class ConfigurationEnumDisplayAdapter extends AbstractDisplayAdapter {

    @Override
    public void placeWidgets(
                             ConfigHolder<?> holder,
                             ConfigValue<?> value,
                             Field field,
                             ConfigTheme theme,
                             WidgetAdder widgets) {
        placeEnumWidgets((EnumValue<?>) value, theme, widgets);
    }

    private <E extends Enum<E>> void placeEnumWidgets(EnumValue<E> value, ConfigTheme theme, WidgetAdder widgets) {
        LocalizedEnumWidget<E> widget = widgets.addConfigWidget((x, y, width, height, name) -> {
            int left = WidgetPlacerHelper.getLeft(x, width);
            int widgetWidth = WidgetPlacerHelper.getWidth(width);
            return new LocalizedEnumWidget<>(left, y, widgetWidth, height, theme, value);
        });
        widget.setBackgroundRenderer(theme.getButtonBackground(widget));
        createControls(
                widget,
                value,
                theme,
                widgets,
                restoreDefault -> widget.setValue(restoreDefault ?
                        value.getValueData().getDefaultValue() :
                        value.getActiveValue()));
    }

    private static final class LocalizedEnumWidget<E extends Enum<E>> extends EnumWidget<E> {

        private final EnumValue<E> value;

        private LocalizedEnumWidget(
                                    int x,
                                    int y,
                                    int width,
                                    int height,
                                    ConfigTheme theme,
                                    EnumValue<E> value) {
            super(x, y, width, height, theme, value);
            this.value = value;
            updateMessage();
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            super.onClick(mouseX, mouseY);
            updateMessage();
        }

        @Override
        public void setValue(E value) {
            super.setValue(value);
            updateMessage();
        }

        private void updateMessage() {
            setMessage(label(this.value.get()));
        }

        private static Component label(Enum<?> value) {
            if (value instanceof CraftingQuantityMode mode) {
                return Component.translatable(switch (mode) {
                    case NET_NEW -> "gui.data_energistics.trinity_quantity.net_new";
                    case FINAL_TOTAL -> "gui.data_energistics.trinity_quantity.final_total";
                });
            }
            if (value instanceof BlankPatternSource source) {
                return Component.translatable(switch (source) {
                    case NETWORK_STORAGE -> "config.data_energistics.enum.blank_pattern_source.network_storage";
                    case LOCAL_SLOT -> "config.data_energistics.enum.blank_pattern_source.local_slot";
                });
            }
            return Component.literal(value.name());
        }
    }
}
