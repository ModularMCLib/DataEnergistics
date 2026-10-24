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

/** Scan behavior is server-owned; HUD appearance remains in the existing client module editor. */
final class PlatformProspectingPanel extends UIElement {

    private final WeaponPlatformToggleRow enabled;
    private final TextField radius = new TextField();

    PlatformProspectingPanel(Player player) {
        addClass("platform-shield-settings");
        enabled = new WeaponPlatformToggleRow(Component.translatable("gui.data_energistics.weapon_platform.ability.prospecting"), on -> {
            if (player.level().isClientSide) RPCPacketDistributor.rpcToServer(PlatformRpc.REQUEST, PlatformRpc.ABILITY, 0, PlatformAbility.PROSPECTING.ordinal());
        });
        enabled.addClass("platform-category-option");
        Label name = new Label();
        name.setValue(Component.translatable("gui.data_energistics.weapon_platform.scan_radius"));
        name.addClasses("platform-label", "platform-shield-status");
        radius.addClasses("platform-editor-input", "platform-shield-color");
        radius.setTextValidator(value -> value.matches("[0-9]{0,3}"));
        radius.setTextResponder(value -> {
            if (!value.isEmpty()) {
                int amount = Integer.parseInt(value);
                if (amount >= 16 && amount <= 384 && player.level().isClientSide) RPCPacketDistributor.rpcToServer(PlatformRpc.REQUEST, PlatformRpc.SCAN_RADIUS, 0, amount);
            }
        });
        addChildren(enabled, name, radius);
    }

    void bind(PlatformSnapshot snapshot) {
        enabled.setActive(ItemResearchUpgrades.of(snapshot.chip()).ids().contains(PlatformAbility.PROSPECTING.research));
        enabled.toggle.setOn(snapshot.systems().settings().enabled(PlatformAbility.PROSPECTING), false);
        radius.setValue(Integer.toString(snapshot.systems().settings().scanRadius()), false);
    }
}
