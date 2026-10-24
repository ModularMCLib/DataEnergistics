package com.fish_dan_.data_energistics.client.hud.weapon_platform.ui;

import com.fish_dan_.data_energistics.client.hud.weapon_platform.preferences.HudModule;
import com.fish_dan_.data_energistics.client.hud.weapon_platform.preferences.HudModuleStyle;
import com.fish_dan_.data_energistics.client.hud.weapon_platform.preferences.TacticalHudPreferences;
import com.fish_dan_.data_energistics.common.weapon.platform.ui.WeaponPlatformToggleRow;

import com.lowdragmc.lowdraglib2.configurator.ui.NumberConfigurator;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ColorSelector;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;

import net.minecraft.network.chat.Component;

import java.util.EnumMap;
import java.util.Locale;

/** Native LDLib2 value controls edit one client-owned module; no server settings are changed. */
public final class TacticalHudAppearancePanel extends UIElement {

    public static final int WIDTH = 376, HEIGHT = 340;
    private final Label moduleName = new Label();
    private final EnumMap<HudModule, Button> modules = new EnumMap<>(HudModule.class);
    private final WeaponPlatformToggleRow relations;
    private final EnumMap<ColorChannel, Button> channels = new EnumMap<>(ColorChannel.class);
    private final ColorSelector color = new ColorSelector();
    private HudModule module = HudModule.WEAPONS;
    private ColorChannel channel = ColorChannel.BACKGROUND;
    private long revision = -1;

    public TacticalHudAppearancePanel(Runnable close) {
        addClass("platform-hud-editor");
        label("appearance", "platform-editor-title");
        UIElement navigation = new UIElement();
        navigation.addClass("platform-editor-navigation");
        for (HudModule entry : HudModule.values()) {
            Button button = new Button();
            button.addClass("platform-action");
            button.addClass("platform-editor-module");
            button.setText(Component.translatable("hud.data_energistics.weapon_platform.module." + entry.name().toLowerCase(Locale.ROOT)));
            button.setOnClick(event -> {
                module = entry;
                refresh();
            });
            modules.put(entry, button);
            navigation.addChild(button);
        }
        addChild(navigation);
        label("drag_hint", "platform-editor-hint");
        moduleName.addClass("platform-label");
        moduleName.addClass("platform-editor-module-name");
        moduleName.setAllowHitTest(false);
        addChild(moduleName);
        number("position_x", "platform-editor-position-x", new NumberConfigurator("", () -> TacticalHudPreferences.get(module).x() * 100,
                value -> TacticalHudPreferences.set(module, TacticalHudPreferences.get(module).toBuilder().x(value.doubleValue() / 100).build()), 0d, true)
                .setRange(0, 100).setWheel(0.5));
        number("position_y", "platform-editor-position-y", new NumberConfigurator("", () -> TacticalHudPreferences.get(module).y() * 100,
                value -> TacticalHudPreferences.set(module, TacticalHudPreferences.get(module).toBuilder().y(value.doubleValue() / 100).build()), 0d, true)
                .setRange(0, 100).setWheel(0.5));
        number("scale", "platform-editor-scale", new NumberConfigurator("", () -> TacticalHudPreferences.get(module).scale() * 100,
                value -> TacticalHudPreferences.set(module, TacticalHudPreferences.get(module).toBuilder().scale(value.floatValue() / 100).build()), 100f, true)
                .setRange(50, 200).setWheel(1));

        UIElement colorChannels = new UIElement();
        colorChannels.addClass("platform-editor-channels");
        for (ColorChannel entry : ColorChannel.values()) {
            Button button = new Button();
            button.addClass("platform-action");
            button.addClass("platform-editor-channel");
            button.setText(Component.translatable("hud.data_energistics.weapon_platform." + entry.name().toLowerCase(Locale.ROOT)));
            button.setOnClick(event -> {
                channel = entry;
                refresh();
            });
            channels.put(entry, button);
            colorChannels.addChild(button);
        }
        addChild(colorChannels);
        color.addClass("platform-editor-color");
        color.colorPreview.addClass("platform-editor-preview");
        color.colorSlider.addClass("platform-editor-slider");
        color.alphaSlider.addClass("platform-editor-slider");
        color.alphaSlider.style(style -> style.tooltips(Component.translatable("hud.data_energistics.weapon_platform.opacity")));
        color.hsbButton.setDisplay(false);
        for (int index = 1; index < color.textContainer.getChildren().size(); index++) color.textContainer.getChildren().get(index).setDisplay(false);
        color.setOnColorChangeListener(value -> {
            var style = TacticalHudPreferences.get(module).toBuilder();
            switch (channel) {
                case BACKGROUND -> style.background(value);
                case TEXT_COLOR -> style.textColor(value);
                case BORDER_COLOR -> style.borderColor(value).relationColors(false);
            }
            TacticalHudPreferences.set(module, style.build());
        });
        theme(color);
        addChild(color);

        label("outline", "platform-editor-outline-title");
        number("border_width", "platform-editor-border-width", new NumberConfigurator("", () -> TacticalHudPreferences.get(module).borderWidth(),
                value -> TacticalHudPreferences.set(module, TacticalHudPreferences.get(module).toBuilder().borderWidth(value.intValue()).build()), 0, true)
                .setRange(0, 4).setWheel(1));
        relations = new WeaponPlatformToggleRow(Component.translatable("hud.data_energistics.weapon_platform.relation_colors"),
                on -> TacticalHudPreferences.set(module, TacticalHudPreferences.get(module).toBuilder().relationColors(on).build()));
        relations.addClass("platform-editor-option");
        relations.addClass("platform-editor-relations");
        addChild(relations);
        number("opacity", "platform-editor-opacity", new NumberConfigurator("", () -> (color.getColor() >>> 24) * 100f / 255,
                value -> color.setColor(color.getColor() & 0xffffff | Math.round(value.floatValue() * 255 / 100) << 24), 100f, true)
                .setRange(0, 100).setWheel(1));
        button("hud.data_energistics.weapon_platform.reset", "platform-editor-reset", () -> TacticalHudPreferences.resetAppearance(module));
        button("gui.done", "platform-editor-done", close);
        addEventListener(UIEvents.TICK, event -> {
            if (revision != TacticalHudPreferences.revision()) refresh();
        });
        refresh();
    }

    private void refresh() {
        revision = TacticalHudPreferences.revision();
        HudModuleStyle style = TacticalHudPreferences.get(module);
        moduleName.setValue(Component.translatable("hud.data_energistics.weapon_platform.module." + module.name().toLowerCase(Locale.ROOT)));
        for (var entry : modules.entrySet()) {
            if (entry.getKey() == module) entry.getValue().addClass("selected");
            else entry.getValue().removeClass("selected");
        }
        relations.setDisplay(module == HudModule.TARGETS);
        relations.toggle.setOn(style.relationColors(), false);
        color.setColor(switch (channel) {
            case BACKGROUND -> style.background();
            case TEXT_COLOR -> style.textColor();
            case BORDER_COLOR -> style.borderColor();
        }, false);
        for (var entry : channels.entrySet()) {
            if (entry.getKey() == channel) entry.getValue().addClass("selected");
            else entry.getValue().removeClass("selected");
        }
    }

    private void number(String key, String styleClass, NumberConfigurator input) {
        UIElement field = new UIElement();
        field.addClass("platform-editor-number");
        field.addClass(styleClass);
        Label name = new Label();
        name.setValue(Component.translatable("hud.data_energistics.weapon_platform." + key));
        name.setAllowHitTest(false);
        name.addClass("platform-label");
        name.addClass("platform-editor-number-name");
        input.addClass("platform-editor-number-input");
        theme(input);
        field.addChildren(name, input);
        addChild(field);
    }

    private void button(String key, String styleClass, Runnable action) {
        Button button = new Button();
        button.setText(Component.translatable(key));
        button.addClass("platform-action");
        button.addClass(styleClass);
        button.setOnClick(event -> action.run());
        addChild(button);
    }

    private void label(String key, String styleClass) {
        Label label = new Label();
        label.addClass("platform-label");
        label.addClass(styleClass);
        label.setValue(Component.translatable("hud.data_energistics.weapon_platform." + key));
        label.setAllowHitTest(false);
        addChild(label);
    }

    private static void theme(UIElement element) {
        if (element instanceof Label) element.addClass("platform-editor-label");
        if (element instanceof Button) element.addClass("platform-action");
        if (element instanceof TextField) element.addClass("platform-editor-input");
        for (UIElement child : element.getChildren()) theme(child);
    }

    private enum ColorChannel {
        BACKGROUND,
        TEXT_COLOR,
        BORDER_COLOR
    }
}
