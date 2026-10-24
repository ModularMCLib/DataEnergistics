package com.fish_dan_.data_energistics.common.weapon.platform.ui;

import com.fish_dan_.data_energistics.common.research.item.ItemResearchUpgrades;
import com.fish_dan_.data_energistics.common.weapon.platform.model.PlatformAbility;
import com.fish_dan_.data_energistics.common.weapon.platform.network.PlatformRpc;
import com.fish_dan_.data_energistics.common.weapon.platform.protocol.PlatformSnapshot;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.networking.rpc.RPCPacketDistributor;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/** Shield controls live in the platform defense page; appearance remains an item-owned choice. */
final class PlatformShieldPanel extends UIElement {

    private final WeaponPlatformToggleRow enabled;
    private final Label status = new Label();
    private final TextField color = new TextField();

    PlatformShieldPanel(Player player) {
        addClass("platform-shield-settings");
        enabled = new WeaponPlatformToggleRow(Component.translatable("gui.data_energistics.weapon_platform.ability.shield"), on -> {
            if (player.level().isClientSide) RPCPacketDistributor.rpcToServer(PlatformRpc.REQUEST, PlatformRpc.ABILITY, 0, PlatformAbility.SHIELD.ordinal());
        });
        enabled.addClass("platform-category-option");
        status.addClass("platform-label");
        status.addClass("platform-shield-status");
        color.addClass("platform-editor-input");
        color.addClass("platform-shield-color");
        color.style(style -> style.tooltips(Component.translatable("gui.data_energistics.weapon_platform.shield_color")));
        color.setTextValidator(value -> value.matches("[0-9a-fA-F]{0,8}"));
        color.setTextResponder(value -> {
            if (value.length() == 8 && player.level().isClientSide) RPCPacketDistributor.rpcToServer(PlatformRpc.REQUEST, PlatformRpc.SHIELD_COLOR, 0, (int) Long.parseLong(value, 16));
        });
        addChildren(enabled, status, color);
    }

    void bind(PlatformSnapshot snapshot) {
        boolean researched = ItemResearchUpgrades.of(snapshot.chip()).ids().contains(PlatformAbility.SHIELD.research);
        enabled.setActive(researched);
        enabled.toggle.setOn(snapshot.systems().settings().enabled(PlatformAbility.SHIELD), false);
        var shield = snapshot.systems().shield();
        status.setValue(Component.translatable("gui.data_energistics.weapon_platform.shield_charge", shield.charge(), snapshot.systems().shieldUpgrades().capacity()));
        color.setValue(String.format("%08X", snapshot.systems().settings().shieldColor()), false);
    }
}
