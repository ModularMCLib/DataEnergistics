package com.fish_dan_.data_energistics.common.weapon.platform.ui;

import com.fish_dan_.data_energistics.api.gui.style.SharedBackpackStyles;
import com.fish_dan_.data_energistics.client.weapon.platform.PlatformClientState;
import com.fish_dan_.data_energistics.common.weapon.platform.model.BiochipResearch;
import com.fish_dan_.data_energistics.common.weapon.platform.network.PlatformRpc;
import com.fish_dan_.data_energistics.common.weapon.platform.protocol.PlatformSnapshot;
import com.fish_dan_.data_energistics.common.weapon.platform.storage.WeaponPlatformInventory;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.networking.rpc.RPCPacketDistributor;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.ItemHandlerCopySlot;

import org.jspecify.annotations.Nullable;

import java.util.EnumMap;
import java.util.Locale;
import java.util.function.Consumer;

/** Platform-wide options form inset groups; the sheet owns their lines, spacing and control states. */
final class WeaponPlatformSettingsPanel extends UIElement {

    private final Label capacity = new Label();
    private final Label cooling = new Label();
    private final Label hud = new Label();
    private final Button appearance = new Button();
    private final EnumMap<PlatformSettingCategory, WeaponPlatformSwitch> categories = new EnumMap<>(PlatformSettingCategory.class);
    private final EnumMap<PlatformSettingCategory, Button> gears = new EnumMap<>(PlatformSettingCategory.class);

    WeaponPlatformSettingsPanel(Player player, IItemHandlerModifiable inventory, Consumer<PlatformSettingCategory> openCategory) {
        boolean client = player.level().isClientSide;
        addClass("platform-settings");
        UIElement header = new UIElement();
        header.addClass("platform-settings-header");
        header.addChild(label(new Label(), Component.translatable("gui.data_energistics.weapon_platform.platform_configuration"), "platform-settings-title"));
        ItemSlot chip = new ItemSlot(new ItemHandlerCopySlot(inventory, WeaponPlatformInventory.CHIP_SLOT, 0, 0));
        SharedBackpackStyles.standalone(chip);
        chip.addClass("platform-chip");
        chip.style(style -> style.tooltips(Component.translatable("gui.data_energistics.weapon_platform.chip")));
        header.addChild(chip);
        addChild(header);

        UIElement body = new UIElement();
        body.addClass("platform-settings-body");
        UIElement shadow = new UIElement();
        shadow.addClass("platform-settings-body-shadow");
        shadow.setAllowHitTest(false);
        body.addChild(shadow);
        UIElement content = new UIElement();
        content.addClass("platform-settings-content");
        UIElement research = group("platform-research-group");
        research.addChildren(label(capacity, Component.empty(), "platform-capacity"),
                label(cooling, Component.empty(), "platform-cooling"), label(hud, Component.empty(), "platform-hud-status"));
        appearance.addClass("platform-action");
        appearance.addClass("platform-appearance-button");
        appearance.setText(Component.translatable("gui.data_energistics.weapon_platform.display_settings"));
        appearance.style(style -> style.tooltips(Component.translatable("hud.data_energistics.weapon_platform.appearance")));
        appearance.setOnClick(event -> { if (client) PlatformClientState.openAppearance(); });
        research.addChild(appearance);

        UIElement entries = new UIElement();
        entries.addClass("platform-setting-categories");
        for (PlatformSettingCategory category : PlatformSettingCategory.values()) {
            Component name = Component.translatable("gui.data_energistics.weapon_platform.category." + category.name().toLowerCase(Locale.ROOT));
            UIElement row = new UIElement();
            row.addClass("platform-category-entry");
            row.addChild(label(new Label(), name, "platform-category-entry-name"));
            WeaponPlatformSwitch toggle = new WeaponPlatformSwitch();
            toggle.addClass("platform-category-entry-toggle");
            toggle.style(style -> style.tooltips(name));
            int operation = switch (category) {
                case HUD -> PlatformRpc.HUD;
                case ATTACK -> PlatformRpc.ATTACK;
                case DEFENSE -> PlatformRpc.PROTECTION;
            };
            toggle.setOnSwitchChanged(on -> {
                if (client) RPCPacketDistributor.rpcToServer(PlatformRpc.REQUEST, operation, 0, 0);
            });
            categories.put(category, toggle);
            Button gear = new Button();
            gear.noText();
            gear.addClass("platform-action");
            gear.addClass("platform-category-entry-gear");
            gear.style(style -> style.tooltips(name));
            gear.setOnClick(event -> { if (client) openCategory.accept(category); });
            UIElement icon = new UIElement();
            icon.addClass("platform-category-entry-gear-icon");
            icon.setAllowHitTest(false);
            gear.addChild(icon);
            gears.put(category, gear);
            row.addChildren(toggle, gear);
            entries.addChild(row);
        }

        content.addChildren(entries, research);
        body.addChild(content);
        addChild(body);
    }

    void bind(PlatformSnapshot value, @Nullable PlatformSettingCategory selected) {
        capacity.setValue(Component.translatable("gui.data_energistics.weapon_platform.capacity", value.capacity()));
        cooling.setValue(Component.translatable("gui.data_energistics.weapon_platform.cooling", BiochipResearch.cooling(value.chip())));
        hud.setValue(Component.translatable("gui.data_energistics.weapon_platform.hud", value.hud() ? "✓" : "—"));
        appearance.setActive(value.hud());
        categories.get(PlatformSettingCategory.HUD).setOn(value.hudEnabled(), false);
        categories.get(PlatformSettingCategory.ATTACK).setOn(value.attackEnabled(), false);
        categories.get(PlatformSettingCategory.DEFENSE).setOn(value.friendlyProtection(), false);
        for (PlatformSettingCategory category : PlatformSettingCategory.values()) {
            boolean available = category != PlatformSettingCategory.HUD || value.hud();
            categories.get(category).setActive(available);
            Button gear = gears.get(category);
            gear.setActive(available);
            if (selected == category) gear.addClass("selected");
            else gear.removeClass("selected");
        }
    }

    private static UIElement group(String name) {
        UIElement group = new UIElement();
        group.addClass("platform-settings-group");
        group.addClass(name);
        for (String line : new String[] { "vertical", "top", "bottom" }) {
            UIElement edge = new UIElement();
            edge.addClass("platform-group-" + line);
            edge.setAllowHitTest(false);
            group.addChild(edge);
        }
        return group;
    }

    private static Label label(Label label, Component text, String name) {
        label.setValue(text);
        label.setAllowHitTest(false);
        label.addClass("platform-label");
        label.addClass(name);
        return label;
    }
}
