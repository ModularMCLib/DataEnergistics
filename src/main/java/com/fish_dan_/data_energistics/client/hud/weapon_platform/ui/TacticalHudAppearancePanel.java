package com.fish_dan_.data_energistics.client.hud.weapon_platform.ui;

import com.fish_dan_.data_energistics.client.hud.weapon_platform.preferences.HudModule;
import com.fish_dan_.data_energistics.client.hud.weapon_platform.preferences.HudModuleStyle;
import com.fish_dan_.data_energistics.client.hud.weapon_platform.preferences.TacticalHudPreferences;

import com.lowdragmc.lowdraglib2.configurator.ui.NumberConfigurator;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ColorSelector;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;

import net.minecraft.network.chat.Component;

import it.unimi.dsi.fastutil.objects.ObjectList;

import java.util.EnumMap;
import java.util.Locale;

import static com.fish_dan_.data_energistics.common.weapon.platform.ui.WeaponPlatformStyle.*;

/** Native LDLib2 value controls edit one client-owned module; no server settings are changed. */
public final class TacticalHudAppearancePanel extends UIElement {

    public static final int WIDTH = 360, HEIGHT = 340;
    private final Button moduleSelector;
    private final Button visibility;
    private final Button relations;
    private final Button[] fieldButtons = new Button[7];
    private final EnumMap<ColorChannel, Button> channels = new EnumMap<>(ColorChannel.class);
    private final ColorSelector color = new ColorSelector();
    private HudModule module = HudModule.WEAPONS;
    private ColorChannel channel = ColorChannel.BACKGROUND;
    private long revision = -1;

    public TacticalHudAppearancePanel(Runnable close) {
        addClass("platform-hud-editor");
        moduleSelector = button("", 8, 6, 236, 20, () -> {
            module = HudModule.values()[(module.ordinal() + 1) % HudModule.values().length];
            refresh();
        });
        visibility = button("", 252, 6, 100, 20, () -> {
            var style = TacticalHudPreferences.get(module);
            TacticalHudPreferences.set(module, style.toBuilder().visible(!style.visible()).build());
        });
        number("position_x", 8, 30, 108, new NumberConfigurator("", () -> TacticalHudPreferences.get(module).x() * 100,
                value -> TacticalHudPreferences.set(module, TacticalHudPreferences.get(module).toBuilder().x(value.doubleValue() / 100).build()), 0d, true)
                .setRange(0, 100).setWheel(0.5));
        number("position_y", 126, 30, 108, new NumberConfigurator("", () -> TacticalHudPreferences.get(module).y() * 100,
                value -> TacticalHudPreferences.set(module, TacticalHudPreferences.get(module).toBuilder().y(value.doubleValue() / 100).build()), 0d, true)
                .setRange(0, 100).setWheel(0.5));
        number("scale", 244, 30, 108, new NumberConfigurator("", () -> TacticalHudPreferences.get(module).scale() * 100,
                value -> TacticalHudPreferences.set(module, TacticalHudPreferences.get(module).toBuilder().scale(value.floatValue() / 100).build()), 100f, true)
                .setRange(50, 200).setWheel(1));
        for (ColorChannel entry : ColorChannel.values()) {
            Button button = button("hud.data_energistics.weapon_platform." + entry.name().toLowerCase(Locale.ROOT),
                    8 + entry.ordinal() * 118, 62, 108, 20, () -> {
                        channel = entry;
                        refresh();
                    });
            button.addClass("platform-editor-channel");
            channels.put(entry, button);
        }
        number("border_width", 8, 88, 144, new NumberConfigurator("", () -> TacticalHudPreferences.get(module).borderWidth(),
                value -> TacticalHudPreferences.set(module, TacticalHudPreferences.get(module).toBuilder().borderWidth(value.intValue()).build()), 0, true)
                .setRange(0, 4).setWheel(1));
        relations = button("", 160, 93, 192, 20, () -> {
            var style = TacticalHudPreferences.get(module);
            TacticalHudPreferences.set(module, style.toBuilder().relationColors(!style.relationColors()).build());
        });
        place(color, 8, 121, 132, 206);
        color.colorPreview.addClass("platform-editor-preview");
        color.colorSlider.addClass("platform-editor-slider");
        color.alphaSlider.addClass("platform-editor-slider");
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
        for (int index = 0; index < fieldButtons.length; index++) {
            int field = index;
            fieldButtons[index] = button("", 160, 121 + index * 23, 192, 19, () -> {
                var style = TacticalHudPreferences.get(module);
                TacticalHudPreferences.set(module, style.toBuilder().fields(style.fields() ^ fields().get(field).flag).build());
            });
        }
        button("hud.data_energistics.weapon_platform.reset", 160, 309, 92, 22, () -> TacticalHudPreferences.reset(module));
        button("gui.done", 260, 309, 92, 22, close);
        addEventListener(UIEvents.TICK, event -> {
            if (revision != TacticalHudPreferences.revision()) refresh();
        });
        refresh();
    }

    private void refresh() {
        revision = TacticalHudPreferences.revision();
        HudModuleStyle style = TacticalHudPreferences.get(module);
        moduleSelector.setText(Component.translatable("hud.data_energistics.weapon_platform.module." + module.name().toLowerCase(Locale.ROOT)));
        visibility.setText(Component.translatable("hud.data_energistics.weapon_platform." + (style.visible() ? "hide" : "show")));
        relations.setDisplay(module == HudModule.TARGETS);
        relations.setText(Component.translatable("hud.data_energistics.weapon_platform.relation_colors").append(style.relationColors() ? " ✓" : ""));
        color.setColor(switch (channel) {
            case BACKGROUND -> style.background();
            case TEXT_COLOR -> style.textColor();
            case BORDER_COLOR -> style.borderColor();
        }, false);
        for (var entry : channels.entrySet()) {
            if (entry.getKey() == channel) entry.getValue().addClass("selected");
            else entry.getValue().removeClass("selected");
        }
        var fields = fields();
        for (int index = 0; index < fieldButtons.length; index++) {
            fieldButtons[index].setDisplay(index < fields.size());
            if (index < fields.size()) {
                var field = fields.get(index);
                fieldButtons[index].setText(Component.translatable("hud.data_energistics.weapon_platform.field." + field.name().toLowerCase(Locale.ROOT))
                        .append(style.shows(field.flag) ? " ✓" : ""));
            }
        }
    }

    private ObjectList<DisplayField> fields() {
        return switch (module) {
            case COMPASS -> ObjectList.of(DisplayField.DIRECTIONS, DisplayField.BEARING, DisplayField.TICKS);
            case WEAPONS -> ObjectList.of(DisplayField.NAME, DisplayField.HEAT, DisplayField.AMMUNITION);
            case NETWORK -> ObjectList.of(DisplayField.CONNECTION, DisplayField.ENERGY, DisplayField.FLOW);
            case TARGETS -> ObjectList.of(DisplayField.NAME, DisplayField.DISTANCE, DisplayField.HEALTH, DisplayField.BINARY, DisplayField.TARGET);
            case GUIDES -> ObjectList.of(DisplayField.LASER, DisplayField.TRAJECTORY, DisplayField.IMPACT);
            case CONTROLS -> ObjectList.of();
        };
    }

    private void number(String key, int x, int y, int width, NumberConfigurator input) {
        Label name = new Label();
        name.setValue(Component.translatable("hud.data_energistics.weapon_platform." + key));
        name.setAllowHitTest(false);
        name.addClass("platform-label");
        place(name, x, y, width, 9);
        addChild(name);
        place(input, x, y + 11, width, 14);
        theme(input);
        addChild(input);
    }

    private Button button(String key, int x, int y, int width, int height, Runnable action) {
        Button button = new Button();
        button.setText(Component.translatable(key));
        button.addClass("platform-action");
        button.setOnClick(event -> action.run());
        place(button, x, y, width, height);
        addChild(button);
        return button;
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

    private enum DisplayField {

        NAME(HudModuleStyle.NAME),
        DISTANCE(HudModuleStyle.DISTANCE),
        HEALTH(HudModuleStyle.HEALTH),
        BINARY(HudModuleStyle.BINARY),
        HEAT(HudModuleStyle.HEAT),
        AMMUNITION(HudModuleStyle.AMMUNITION),
        TARGET(HudModuleStyle.TARGET),
        LASER(HudModuleStyle.LASER),
        TRAJECTORY(HudModuleStyle.TRAJECTORY),
        IMPACT(HudModuleStyle.IMPACT),
        DIRECTIONS(HudModuleStyle.DIRECTIONS),
        BEARING(HudModuleStyle.BEARING),
        TICKS(HudModuleStyle.TICKS),
        CONNECTION(HudModuleStyle.CONNECTION),
        ENERGY(HudModuleStyle.ENERGY),
        FLOW(HudModuleStyle.FLOW);

        private final int flag;

        DisplayField(int flag) {
            this.flag = flag;
        }
    }
}
