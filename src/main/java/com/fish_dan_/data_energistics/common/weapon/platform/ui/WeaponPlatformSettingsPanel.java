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

/** Platform-wide options form inset groups; the sheet owns their lines, spacing and control states. */
final class WeaponPlatformSettingsPanel extends UIElement {

    private final Label capacity = new Label();
    private final Label cooling = new Label();
    private final Label hud = new Label();
    private final Button appearance = new Button();
    private final WeaponPlatformToggleRow[] targets = new WeaponPlatformToggleRow[5];
    private final WeaponPlatformToggleRow protection;
    private final boolean client;

    WeaponPlatformSettingsPanel(Player player, IItemHandlerModifiable inventory) {
        client = player.level().isClientSide;
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
        UIElement research = group("platform-research-group");
        research.addChildren(label(capacity, Component.empty(), "platform-capacity"),
                label(cooling, Component.empty(), "platform-cooling"), label(hud, Component.empty(), "platform-hud-status"));
        appearance.addClass("platform-action");
        appearance.addClass("platform-appearance-button");
        appearance.setText(Component.translatable("gui.data_energistics.weapon_platform.display_settings"));
        appearance.style(style -> style.tooltips(Component.translatable("hud.data_energistics.weapon_platform.appearance")));
        appearance.setOnClick(event -> { if (client) PlatformClientState.openAppearance(); });
        research.addChild(appearance);

        UIElement targeting = group("platform-targeting-group");
        String[] categories = { "players", "hostile", "friendly", "neutral", "other" };
        for (int index = 0; index < targets.length; index++) {
            targets[index] = setting(targeting, "targets." + categories[index], PlatformRpc.TARGETS, 1 << index);
        }
        UIElement safety = group("platform-safety-group");
        protection = setting(safety, "protection", PlatformRpc.PROTECTION, 0);
        body.addChildren(research, targeting, safety);
        addChild(body);
    }

    void bind(PlatformSnapshot value) {
        capacity.setValue(Component.translatable("gui.data_energistics.weapon_platform.capacity", value.capacity()));
        cooling.setValue(Component.translatable("gui.data_energistics.weapon_platform.cooling", BiochipResearch.cooling(value.chip())));
        hud.setValue(Component.translatable("gui.data_energistics.weapon_platform.hud", value.hud() ? "✓" : "—"));
        appearance.setActive(value.hud());
        for (int index = 0; index < targets.length; index++) targets[index].toggle.setOn((value.targetMask() & 1 << index) != 0, false);
        protection.toggle.setOn(value.friendlyProtection(), false);
    }

    private WeaponPlatformToggleRow setting(UIElement group, String key, int operation, int argument) {
        WeaponPlatformToggleRow row = new WeaponPlatformToggleRow(Component.translatable("gui.data_energistics.weapon_platform." + key),
                on -> { if (client) RPCPacketDistributor.rpcToServer(PlatformRpc.REQUEST, operation, 0, argument); });
        row.addClass("platform-setting-row");
        group.addChild(row);
        return row;
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
