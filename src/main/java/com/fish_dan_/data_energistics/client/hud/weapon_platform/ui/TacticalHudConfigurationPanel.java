package com.fish_dan_.data_energistics.client.hud.weapon_platform.ui;

import com.fish_dan_.data_energistics.client.hud.weapon_platform.preferences.HudModule;
import com.fish_dan_.data_energistics.client.hud.weapon_platform.preferences.HudModuleStyle;
import com.fish_dan_.data_energistics.client.hud.weapon_platform.preferences.TacticalHudPreferences;
import com.fish_dan_.data_energistics.client.weapon.platform.PlatformClientState;
import com.fish_dan_.data_energistics.common.weapon.platform.network.PlatformRpc;
import com.fish_dan_.data_energistics.common.weapon.platform.protocol.PlatformSnapshot;
import com.fish_dan_.data_energistics.common.weapon.platform.ui.WeaponPlatformScrollView;
import com.fish_dan_.data_energistics.common.weapon.platform.ui.WeaponPlatformToggleRow;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.networking.rpc.RPCPacketDistributor;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import org.jetbrains.annotations.UnknownNullability;

import java.util.EnumMap;
import java.util.Locale;

/** HUD module visibility, contents and shield color within the full platform menu's HUD category. */
public final class TacticalHudConfigurationPanel extends WeaponPlatformScrollView {

    private final EnumMap<HudModule, WeaponPlatformToggleRow> modules = new EnumMap<>(HudModule.class);
    private final EnumMap<HudModule, ObjectList<FieldToggle>> fields = new EnumMap<>(HudModule.class);
    private final TextField shieldColor = new TextField();
    private long revision = -1;

    public TacticalHudConfigurationPanel(Player player) {
        addClass("platform-category-scroll");
        UIElement content = new UIElement();
        content.addClass("platform-category-content");
        Button appearance = new Button();
        appearance.setText(Component.translatable("hud.data_energistics.weapon_platform.appearance"));
        appearance.addClass("platform-action");
        appearance.addClass("platform-hud-appearance-entry");
        appearance.setOnClick(event -> PlatformClientState.openAppearance());
        content.addChild(appearance);
        UIElement shieldAppearance = new UIElement();
        shieldAppearance.addClass("platform-hud-module-group");
        Label shieldTitle = new Label();
        shieldTitle.setValue(Component.translatable("gui.data_energistics.weapon_platform.shield_color"));
        shieldTitle.addClasses("platform-label", "platform-shield-status");
        shieldTitle.setAllowHitTest(false);
        shieldColor.addClasses("platform-editor-input", "platform-shield-color");
        shieldColor.setTextValidator(value -> value.matches("[0-9a-fA-F]{0,8}"));
        shieldColor.setTextResponder(value -> {
            if (value.length() == 8 && player.level().isClientSide) RPCPacketDistributor.rpcToServer(PlatformRpc.REQUEST, PlatformRpc.SHIELD_COLOR, 0, (int) Long.parseLong(value, 16));
        });
        shieldAppearance.addChildren(shieldTitle, shieldColor);
        content.addChild(shieldAppearance);
        for (HudModule module : HudModule.values()) {
            UIElement group = new UIElement();
            group.addClass("platform-hud-module-group");
            WeaponPlatformToggleRow heading = createHeading(module);
            modules.put(module, heading);
            group.addChild(heading);
            UIElement options = new UIElement();
            options.addClass("platform-hud-module-options");
            for (String side : new String[] { "vertical", "top", "bottom" }) {
                UIElement edge = new UIElement();
                edge.addClass("platform-group-" + side);
                edge.setAllowHitTest(false);
                options.addChild(edge);
            }
            ObjectList<DisplayField> moduleFields = switch (module) {
                case COMPASS -> ObjectList.of(DisplayField.DIRECTIONS, DisplayField.BEARING, DisplayField.TICKS);
                case WEAPONS -> ObjectList.of(DisplayField.NAME, DisplayField.HEAT, DisplayField.AMMUNITION);
                case NETWORK -> ObjectList.of(DisplayField.CONNECTION, DisplayField.ENERGY, DisplayField.FLOW);
                case TARGETS -> ObjectList.of(DisplayField.NAME, DisplayField.DISTANCE, DisplayField.SPEED, DisplayField.ALTITUDE, DisplayField.MOTION,
                        DisplayField.HEALTH, DisplayField.BINARY, DisplayField.TARGET);
                case GUIDES -> ObjectList.of(DisplayField.LASER, DisplayField.TRAJECTORY, DisplayField.IMPACT);
                case ORES -> ObjectList.of(DisplayField.NAME, DisplayField.DISTANCE);
            };
            ObjectList<FieldToggle> fieldRows = new ObjectArrayList<>();
            for (DisplayField field : moduleFields) {
                WeaponPlatformToggleRow row = createRow(module, field);
                fieldRows.add(new FieldToggle(field, row));
                options.addChild(row);
            }
            fields.put(module, fieldRows);
            group.addChild(options);
            content.addChild(group);
        }
        addScrollViewChild(content);
        addEventListener(UIEvents.TICK, event -> {
            if (revision != TacticalHudPreferences.revision()) refresh();
        });
        refresh();
    }

    public void bind(PlatformSnapshot snapshot) {
        if (!shieldColor.isFocused()) shieldColor.setValue(String.format(Locale.ROOT, "%08X", snapshot.systems().settings().shieldColor()), false);
    }

    private WeaponPlatformToggleRow createHeading(@UnknownNullability HudModule module) {
        WeaponPlatformToggleRow heading = new WeaponPlatformToggleRow(Component.translatable(
                "hud.data_energistics.weapon_platform.module." + module.name().toLowerCase(Locale.ROOT)), on -> TacticalHudPreferences.set(module, TacticalHudPreferences.get(module).toBuilder().visible(on).build()));
        heading.addClass("platform-hud-module-heading");
        UIElement divider = new UIElement();
        divider.addClass("platform-hud-module-divider");
        divider.setAllowHitTest(false);
        heading.addChild(divider);
        return heading;
    }

    private WeaponPlatformToggleRow createRow(HudModule module, DisplayField field) {
        WeaponPlatformToggleRow row = new WeaponPlatformToggleRow(Component.translatable(
                "hud.data_energistics.weapon_platform.field." + field.name().toLowerCase(Locale.ROOT)), on -> {
                    HudModuleStyle style = TacticalHudPreferences.get(module);
                    TacticalHudPreferences.set(module, style.toBuilder().fields(on ? style.fields() | field.flag : style.fields() & ~field.flag).build());
                });
        row.addClass("platform-category-option");
        row.addClass("platform-hud-field");
        return row;
    }

    private void refresh() {
        revision = TacticalHudPreferences.revision();
        for (HudModule module : HudModule.values()) {
            HudModuleStyle style = TacticalHudPreferences.get(module);
            modules.get(module).toggle.setOn(style.visible(), false);
            for (FieldToggle field : fields.get(module)) field.row.toggle.setOn(style.shows(field.field.flag), false);
        }
    }

    private record FieldToggle(DisplayField field, WeaponPlatformToggleRow row) {}

    private enum DisplayField {

        NAME(HudModuleStyle.NAME),
        DISTANCE(HudModuleStyle.DISTANCE),
        SPEED(HudModuleStyle.SPEED),
        ALTITUDE(HudModuleStyle.ALTITUDE),
        MOTION(HudModuleStyle.MOTION),
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
