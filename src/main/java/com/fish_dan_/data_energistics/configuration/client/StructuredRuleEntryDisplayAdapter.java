package com.fish_dan_.data_energistics.configuration.client;

import com.fish_dan_.data_energistics.configuration.rules.schema.DataProductionRuleConfigurationAdapter.EntryDefinition;
import com.fish_dan_.data_energistics.configuration.rules.schema.DataProductionRuleConfigurationAdapter.StructuredRuleEntryArrayValue;
import com.fish_dan_.data_energistics.configuration.rules.schema.DataProductionRuleConfigurationAdapter.StructuredRuleEntryValue;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;

import dev.toma.configuration.client.WidgetAdder;
import dev.toma.configuration.client.screen.ConfigScreen;
import dev.toma.configuration.client.screen.WidgetPlacerHelper;
import dev.toma.configuration.client.theme.ConfigTheme;
import dev.toma.configuration.client.theme.adapter.AbstractArrayDisplayAdapter;
import dev.toma.configuration.client.theme.adapter.AbstractDisplayAdapter;
import dev.toma.configuration.client.theme.adapter.DisplayAdapter;
import dev.toma.configuration.client.theme.adapter.ObjectDisplayAdapter;
import dev.toma.configuration.client.widget.ConfigEntryWidget;
import dev.toma.configuration.client.widget.ThemedButtonWidget;
import dev.toma.configuration.config.ConfigHolder;
import dev.toma.configuration.config.adapter.TypeMatcher;
import dev.toma.configuration.config.value.ConfigValue;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;

import java.lang.reflect.Field;

/** Client widgets for the record-array and record-entry configuration hierarchy. */
public final class StructuredRuleEntryDisplayAdapter<T> extends AbstractArrayDisplayAdapter<T> {

    private final EntryDefinition<T> definition;

    public StructuredRuleEntryDisplayAdapter(EntryDefinition<T> definition) {
        this.definition = definition;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void placeWidgets(
                             ConfigHolder<?> holder,
                             ConfigValue<?> value,
                             Field field,
                             ConfigTheme theme,
                             WidgetAdder widgets) {
        StructuredRuleEntryArrayValue<T> array = (StructuredRuleEntryArrayValue<T>) value;
        Class<T> entryType = (Class<T>) field.getType().getComponentType();
        ThemedButtonWidget widget = initButton(
                widgets,
                theme,
                holder,
                array,
                entryType,
                field,
                data -> array.createElementValue(data));
        createControls(
                widget,
                array,
                theme,
                widgets,
                createReverter(array, widget));
    }

    /** Replaces the library object adapter for this holder and delegates ordinary objects to it. */
    public static final class EntryDisplayAdapter extends AbstractDisplayAdapter {

        private final DisplayAdapter fallback = new ObjectDisplayAdapter();

        @Override
        public void placeWidgets(
                                 ConfigHolder<?> holder,
                                 ConfigValue<?> value,
                                 Field field,
                                 ConfigTheme theme,
                                 WidgetAdder widgets) {
            if (!(value instanceof StructuredRuleEntryValue<?> entry)) {
                fallback.placeWidgets(holder, value, field, theme, widgets);
                return;
            }
            Object2ObjectLinkedOpenHashMap<String, ConfigValue<?>> fields = entry.fields();
            ThemedButtonWidget widget = widgets.addConfigWidget((x, y, width, height, name) -> {
                int left = WidgetPlacerHelper.getLeft(x, width);
                int widgetWidth = WidgetPlacerHelper.getWidth(width);
                return new ThemedButtonWidget(
                        left,
                        y,
                        widgetWidth,
                        height,
                        ConfigEntryWidget.OPEN,
                        theme);
            });
            widget.setBackgroundRenderer(theme.getButtonBackground(widget));
            widget.setClickListener((ignored, mouseX, mouseY) -> {
                Minecraft minecraft = Minecraft.getInstance();
                Screen parent = minecraft.screen;
                minecraft.setScreen(new ConfigScreen(holder, widgets.getComponentName(), fields, parent));
            });
            createControls(
                    widget,
                    entry,
                    theme,
                    widgets,
                    restoreDefault -> {
                        if (restoreDefault) {
                            entry.revertChangesToDefault();
                        } else {
                            entry.revertChanges();
                        }
                        widget.setChanged();
                    });
        }
    }

    public static TypeMatcher arrayMatcher(ResourceLocation id, Class<?> type) {
        return new TypeMatcher.NamedMatcherImpl(id, type::equals);
    }
}
