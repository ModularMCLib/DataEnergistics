package com.fish_dan_.data_energistics.common.weapon.platform.ui;

import com.fish_dan_.data_energistics.common.research.item.ItemResearchUpgrades;
import com.fish_dan_.data_energistics.common.weapon.platform.model.PlatformAbility;
import com.fish_dan_.data_energistics.common.weapon.platform.network.PlatformRpc;
import com.fish_dan_.data_energistics.common.weapon.platform.protocol.PlatformSnapshot;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.networking.rpc.RPCPacketDistributor;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/** Shield activation and charge stay in defense; its appearance is edited in the HUD configuration. */
final class PlatformShieldPanel extends UIElement {

    private final WeaponPlatformToggleRow enabled;
    private final Label status = new Label();

    PlatformShieldPanel(Player player) {
        addClass("platform-shield-settings");
        enabled = new WeaponPlatformToggleRow(Component.translatable("gui.data_energistics.weapon_platform.ability.shield"), on -> {
            if (player.level().isClientSide) RPCPacketDistributor.rpcToServer(PlatformRpc.REQUEST, PlatformRpc.ABILITY, 0, PlatformAbility.SHIELD.ordinal());
        });
        enabled.addClass("platform-category-option");
        status.addClass("platform-label");
        status.addClass("platform-shield-status");
        addChildren(enabled, status);
    }

    void bind(PlatformSnapshot snapshot) {
        boolean researched = ItemResearchUpgrades.of(snapshot.chip()).ids().contains(PlatformAbility.SHIELD.research);
        enabled.setActive(researched);
        enabled.toggle.setOn(snapshot.systems().settings().enabled(PlatformAbility.SHIELD), false);
        var shield = snapshot.systems().shield();
        status.setValue(Component.translatable("gui.data_energistics.weapon_platform.shield_charge", shield.charge(), snapshot.systems().shieldUpgrades().capacity()));
    }
}
