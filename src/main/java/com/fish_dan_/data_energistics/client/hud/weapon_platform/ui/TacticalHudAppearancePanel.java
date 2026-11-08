package com.fish_dan_.data_energistics.client.hud.weapon_platform.ui;

import com.fish_dan_.data_energistics.client.hud.weapon_platform.preferences.TacticalHudPreferences;
import com.fish_dan_.data_energistics.client.weapon.platform.PlatformClientState;
import com.fish_dan_.data_energistics.common.research.item.ItemResearchUpgrades;
import com.fish_dan_.data_energistics.common.weapon.platform.model.HudModule;
import com.fish_dan_.data_energistics.common.weapon.platform.model.PlatformAbility;
import com.fish_dan_.data_energistics.common.weapon.platform.network.PlatformRpc;
import com.fish_dan_.data_energistics.common.weapon.platform.shield.ShieldAppearance;
import com.fish_dan_.data_energistics.common.weapon.platform.ui.WeaponPlatformScrollView;
import com.fish_dan_.data_energistics.common.weapon.platform.ui.WeaponPlatformToggleRow;
import com.fish_dan_.data_energistics.config.client.hud.HudModuleStyle;
import com.fish_dan_.data_energistics.config.client.hud.tactical.TacticalHudWidget;

import com.lowdragmc.lowdraglib2.configurator.ui.NumberConfigurator;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ColorSelector;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.networking.rpc.RPCPacketDistributor;

import net.minecraft.network.chat.Component;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;

import java.util.EnumMap;
import java.util.Locale;
import java.util.function.Consumer;

/** LDLib2 appearance controls persist client preferences; shield cosmetics also synchronize to observers. */
public final class TacticalHudAppearancePanel extends UIElement {

    public static final int WIDTH = 376, HEIGHT = 340;
    private final Label moduleName = new Label();
    private final EnumMap<HudModule, Button> modules = new EnumMap<>(HudModule.class);
    private final EnumMap<TacticalHudWidget, Button> widgets = new EnumMap<>(TacticalHudWidget.class);
    private final Button shield = new Button();
    private final ObjectList<UIElement> moduleControls = new ObjectArrayList<>();
    private final WeaponPlatformToggleRow relations;
    private final WeaponPlatformToggleRow widgetVisibility;
    private final EnumMap<ColorChannel, Button> channels = new EnumMap<>(ColorChannel.class);
    private final ColorSelector color = new ColorSelector();
    private HudModule module = HudModule.WEAPONS;
    private TacticalHudWidget widget = TacticalHudWidget.LEFT_WEAPONS;
    private boolean widgetSelected = true;
    private ColorChannel channel = ColorChannel.TEXT_COLOR;
    private long revision = -1;
    private ShieldAppearance shieldAppearance = ShieldAppearance.DEFAULT;
    private final Button shieldDisplay = new Button();
    private boolean shieldUnlocked;
    private boolean shieldSelected;

    public TacticalHudAppearancePanel(Runnable close) {
        addClass("platform-hud-editor");
        label("appearance", "platform-editor-title");
        WeaponPlatformScrollView navigation = new WeaponPlatformScrollView();
        navigation.addClass("platform-editor-navigation");
        UIElement entries = new UIElement();
        entries.addClass("platform-editor-navigation-list");
        for (HudModule entry : HudModule.values()) {
            if (entry == HudModule.WEAPONS || entry == HudModule.NETWORK) continue;
            Button button = new Button();
            button.addClass("platform-action");
            button.addClass("platform-editor-module");
            button.setText(Component.translatable("hud.data_energistics.weapon_platform.module." + entry.name().toLowerCase(Locale.ROOT)));
            button.setOnClick(event -> {
                module = entry;
                widgetSelected = false;
                channel = entry == HudModule.COMPASS ? ColorChannel.TEXT_COLOR : ColorChannel.BACKGROUND;
                shieldSelected = false;
                refresh();
            });
            modules.put(entry, button);
            entries.addChild(button);
        }
        for (TacticalHudWidget entry : TacticalHudWidget.values()) {
            Button button = new Button();
            button.addClasses("platform-action", "platform-editor-module");
            button.setText(Component.translatable(entry.translationKey()));
            button.setOnClick(event -> {
                widget = entry;
                widgetSelected = true;
                shieldSelected = false;
                channel = ColorChannel.TEXT_COLOR;
                refresh();
            });
            widgets.put(entry, button);
            entries.addChild(button);
        }
        shield.addClasses("platform-action", "platform-editor-module");
        shield.setText(Component.translatable("hud.data_energistics.weapon_platform.module.shield"));
        shield.setOnClick(event -> {
            shieldSelected = true;
            channel = ColorChannel.SHIELD_COLOR;
            refresh();
        });
        entries.addChild(shield);
        navigation.addScrollViewChild(entries);
        addChild(navigation);
        label("drag_hint", "platform-editor-hint");
        moduleName.addClass("platform-label");
        moduleName.addClass("platform-editor-module-name");
        moduleName.setAllowHitTest(false);
        addChild(moduleName);
        moduleControls.add(number("position_x", "platform-editor-position-x", new NumberConfigurator("", () -> selectedStyle().x() * 100,
                value -> edit(style -> style.x(value.doubleValue() / 100)), 0d, true)
                .setRange(0, 100).setWheel(0.5)));
        moduleControls.add(number("position_y", "platform-editor-position-y", new NumberConfigurator("", () -> selectedStyle().y() * 100,
                value -> edit(style -> style.y(value.doubleValue() / 100)), 0d, true)
                .setRange(0, 100).setWheel(0.5)));
        moduleControls.add(number("scale", "platform-editor-scale", new NumberConfigurator("", () -> selectedStyle().scale() * 100,
                value -> edit(style -> style.scale(value.floatValue() / 100)), 100f, true)
                .setRange(50, 200).setWheel(1)));

        UIElement colorChannels = new UIElement();
        colorChannels.addClass("platform-editor-channels");
        for (ColorChannel entry : ColorChannel.values()) {
            Button button = new Button();
            button.addClass("platform-action");
            button.addClass("platform-editor-channel");
            if (entry == ColorChannel.SHIELD_COLOR || entry == ColorChannel.SHIELD_BORDER_COLOR) button.addClass("platform-editor-shield-channel");
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
            if (shieldSelected) {
                RPCPacketDistributor.rpcToServer(PlatformRpc.SHIELD_APPEARANCE, channel == ColorChannel.SHIELD_COLOR ? 0 : 1, value);
                return;
            }
            edit(style -> {
                switch (channel) {
                    case BACKGROUND -> style.background(value);
                    case TEXT_COLOR -> style.textColor(value);
                    case BORDER_COLOR -> style.borderColor(value).relationColors(false);
                }
            });
        });
        theme(color);
        addChild(color);

        label("outline", "platform-editor-outline-title");
        number("border_width", "platform-editor-border-width", new NumberConfigurator("", () -> shieldSelected ? shieldAppearance.borderWidth() : selectedStyle().borderWidth(),
                value -> {
                    if (shieldSelected) RPCPacketDistributor.rpcToServer(PlatformRpc.SHIELD_APPEARANCE, 2, value.intValue());
                    else edit(style -> style.borderWidth(value.intValue()));
                }, 0, true).setRange(0, 4).setWheel(1));
        relations = new WeaponPlatformToggleRow(Component.translatable("hud.data_energistics.weapon_platform.relation_colors"),
                on -> TacticalHudPreferences.set(module, TacticalHudPreferences.get(module).toBuilder().relationColors(on).build()));
        relations.addClass("platform-editor-option");
        relations.addClass("platform-editor-relations");
        addChild(relations);
        widgetVisibility = new WeaponPlatformToggleRow(Component.translatable("hud.data_energistics.weapon_platform.widget_visible"),
                on -> edit(style -> style.visible(on)));
        widgetVisibility.addClasses("platform-editor-option", "platform-editor-relations");
        addChild(widgetVisibility);
        number("opacity", "platform-editor-opacity", new NumberConfigurator("", () -> (color.getColor() >>> 24) * 100f / 255,
                value -> color.setColor(color.getColor() & 0xffffff | Math.round(value.floatValue() * 255 / 100) << 24), 100f, true)
                .setRange(0, 100).setWheel(1));
        shieldDisplay.addClasses("platform-action", "platform-editor-shield-display");
        shieldDisplay.setOnClick(event -> RPCPacketDistributor.rpcToServer(PlatformRpc.SHIELD_APPEARANCE, 4,
                (shieldAppearance.display().ordinal() + 1) % ShieldAppearance.Display.values().length));
        shieldDisplay.addEventListener(UIEvents.MOUSE_DOWN, event -> {
            if (event.button != 1) return;
            RPCPacketDistributor.rpcToServer(PlatformRpc.SHIELD_APPEARANCE, 4,
                    Math.floorMod(shieldAppearance.display().ordinal() - 1, ShieldAppearance.Display.values().length));
            event.stopPropagation();
        });
        addChild(shieldDisplay);
        button("hud.data_energistics.weapon_platform.reset", "platform-editor-reset", () -> {
            if (shieldSelected) {
                RPCPacketDistributor.rpcToServer(PlatformRpc.SHIELD_APPEARANCE, 3, 0);
            } else if (widgetSelected) TacticalHudPreferences.resetAppearance(widget);
            else TacticalHudPreferences.resetAppearance(module);
        });
        button("gui.done", "platform-editor-done", close);
        addEventListener(UIEvents.TICK, event -> {
            if (revision != TacticalHudPreferences.getRevision() || !shieldAppearance.equals(PlatformClientState.snapshot.systems().shieldAppearance()) || shieldUnlocked != ItemResearchUpgrades.of(PlatformClientState.snapshot.chip()).ids().contains(PlatformAbility.SHIELD.research)) refresh();
        });
        refresh();
    }

    private void refresh() {
        revision = TacticalHudPreferences.getRevision();
        shieldAppearance = PlatformClientState.snapshot.systems().shieldAppearance();
        shieldUnlocked = ItemResearchUpgrades.of(PlatformClientState.snapshot.chip()).ids().contains(PlatformAbility.SHIELD.research);
        shield.setActive(shieldUnlocked);
        if (!shieldUnlocked && shieldSelected) {
            shieldSelected = false;
            channel = widgetSelected ? ColorChannel.TEXT_COLOR : ColorChannel.BACKGROUND;
        }
        if (shieldSelected) {
            shield.addClass("selected");
            addClass("platform-shield-appearance");
        } else {
            shield.removeClass("selected");
            removeClass("platform-shield-appearance");
        }
        for (UIElement control : moduleControls) control.setDisplay(!shieldSelected);
        HudModuleStyle style = selectedStyle();
        moduleName.setValue(Component.translatable(!shieldSelected && widgetSelected ? widget.translationKey() : "hud.data_energistics.weapon_platform.module." + (shieldSelected ? "shield" : module.getSerializedName())));
        for (var entry : modules.entrySet()) {
            if (!shieldSelected && !widgetSelected && entry.getKey() == module) entry.getValue().addClass("selected");
            else entry.getValue().removeClass("selected");
        }
        for (var entry : widgets.entrySet()) {
            if (!shieldSelected && widgetSelected && entry.getKey() == widget) entry.getValue().addClass("selected");
            else entry.getValue().removeClass("selected");
        }
        relations.setDisplay(!shieldSelected && !widgetSelected && module == HudModule.TARGETS);
        widgetVisibility.setDisplay(!shieldSelected && widgetSelected);
        widgetVisibility.toggle.setOn(style.visible(), false);
        shieldDisplay.setDisplay(shieldSelected);
        shieldDisplay.setText(Component.translatable("hud.data_energistics.weapon_platform.shield_display." + shieldAppearance.display().getSerializedName()));
        relations.toggle.setOn(style.relationColors(), false);
        color.setColor(switch (channel) {
            case BACKGROUND -> style.background();
            case TEXT_COLOR -> style.textColor();
            case BORDER_COLOR -> style.borderColor();
            case SHIELD_COLOR -> shieldAppearance.color();
            case SHIELD_BORDER_COLOR -> shieldAppearance.borderColor();
        }, false);
        for (var entry : channels.entrySet()) {
            entry.getValue().setDisplay(shieldSelected == (entry.getKey() == ColorChannel.SHIELD_COLOR || entry.getKey() == ColorChannel.SHIELD_BORDER_COLOR) && !(!shieldSelected && (widgetSelected || module == HudModule.COMPASS) && entry.getKey() == ColorChannel.BACKGROUND));
            if (entry.getKey() == channel) entry.getValue().addClass("selected");
            else entry.getValue().removeClass("selected");
        }
    }

    private HudModuleStyle selectedStyle() {
        if (!widgetSelected) return TacticalHudPreferences.get(module);
        var style = TacticalHudPreferences.get(widget);
        return HudModuleStyle.builder().x(style.x()).y(style.y()).scale(style.scale()).textColor(style.textColor())
                .borderColor(style.lineColor()).borderWidth(style.lineWidth()).visible(style.visible()).build();
    }

    private void edit(Consumer<HudModuleStyle.HudModuleStyleBuilder> edit) {
        var builder = selectedStyle().toBuilder();
        edit.accept(builder);
        HudModuleStyle style = builder.build();
        if (widgetSelected) {
            TacticalHudPreferences.set(widget, TacticalHudPreferences.get(widget).toBuilder().x(style.x()).y(style.y()).scale(style.scale())
                    .textColor(style.textColor()).lineColor(style.borderColor()).lineWidth(style.borderWidth()).visible(style.visible()).build());
        } else TacticalHudPreferences.set(module, style);
    }

    private UIElement number(String key, String styleClass, NumberConfigurator input) {
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
        return field;
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
        BORDER_COLOR,
        SHIELD_COLOR,
        SHIELD_BORDER_COLOR
    }
}
