package com.fish_dan_.data_energistics.common.weapon.platform.ui;

import com.fish_dan_.data_energistics.common.research.item.ItemResearchUpgrades;
import com.fish_dan_.data_energistics.common.weapon.platform.domain.StasisDomainSavedData;
import com.fish_dan_.data_energistics.common.weapon.platform.model.PlatformAbility;
import com.fish_dan_.data_energistics.common.weapon.platform.network.PlatformRpc;
import com.fish_dan_.data_energistics.common.weapon.platform.protocol.PlatformSnapshot;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.networking.rpc.RPCPacketDistributor;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

final class PlatformDomainPanel extends UIElement {

    private final Button activate = new Button();
    private final WeaponPlatformToggleRow size;
    private final Label status = new Label();

    PlatformDomainPanel(Player player) {
        addClass("platform-shield-settings");
        activate.addClasses("platform-action", "platform-category-option");
        activate.setText(Component.translatable("gui.data_energistics.weapon_platform.domain_toggle"));
        activate.setOnClick(event -> { if (player.level().isClientSide) RPCPacketDistributor.rpcToServer(PlatformRpc.REQUEST, PlatformRpc.DOMAIN, 0, 0); });
        size = new WeaponPlatformToggleRow(Component.translatable("gui.data_energistics.weapon_platform.domain_expanded"), on -> { if (player.level().isClientSide) RPCPacketDistributor.rpcToServer(PlatformRpc.REQUEST, PlatformRpc.DOMAIN_SIZE, 0, on ? 5 : 3); });
        size.addClass("platform-category-option");
        status.addClasses("platform-label", "platform-shield-status");
        addChildren(activate, size, status);
    }

    void bind(PlatformSnapshot snapshot) {
        var research = ItemResearchUpgrades.of(snapshot.chip()).ids();
        var systems = snapshot.systems();
        activate.setActive(research.contains(PlatformAbility.DOMAIN.research) && (systems.domainRemaining() > 0 || systems.domainCooldown() == 0));
        size.setActive(research.contains(StasisDomainSavedData.EXPANSION));
        size.toggle.setOn(systems.settings().domainChunks() == 5, false);
        status.setValue(Component.translatable("gui.data_energistics.weapon_platform.domain_status", systems.domainRemaining() / 20, systems.domainCooldown() / 20));
    }
}
