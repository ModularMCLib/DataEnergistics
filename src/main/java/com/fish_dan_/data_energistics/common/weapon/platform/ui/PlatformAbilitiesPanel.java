package com.fish_dan_.data_energistics.common.weapon.platform.ui;

import com.fish_dan_.data_energistics.common.research.item.ItemResearchUpgrades;
import com.fish_dan_.data_energistics.common.weapon.platform.model.PlatformAbility;
import com.fish_dan_.data_energistics.common.weapon.platform.network.PlatformRpc;
import com.fish_dan_.data_energistics.common.weapon.platform.protocol.PlatformSnapshot;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.networking.rpc.RPCPacketDistributor;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import java.util.EnumMap;

/** Player support, mobility and vision switches share the equipment menu's native toggle component. */
final class PlatformAbilitiesPanel extends UIElement {

    private final EnumMap<PlatformAbility, WeaponPlatformToggleRow> rows = new EnumMap<>(PlatformAbility.class);

    PlatformAbilitiesPanel(Player player) {
        addClass("platform-shield-settings");
        for (PlatformAbility ability : new PlatformAbility[] { PlatformAbility.NIGHT_VISION, PlatformAbility.SATIETY, PlatformAbility.ARMOR, PlatformAbility.FLIGHT, PlatformAbility.MOVEMENT_PROTECTION, PlatformAbility.NOCLIP }) {
            var row = new WeaponPlatformToggleRow(Component.translatable("gui.data_energistics.weapon_platform.ability." + ability.id), on -> {
                if (player.level().isClientSide) RPCPacketDistributor.rpcToServer(PlatformRpc.REQUEST, PlatformRpc.ABILITY, 0, ability.ordinal());
            });
            row.addClass("platform-category-option");
            rows.put(ability, row);
            addChild(row);
        }
    }

    void bind(PlatformSnapshot snapshot) {
        var research = ItemResearchUpgrades.of(snapshot.chip()).ids();
        for (var entry : rows.entrySet()) {
            entry.getValue().setActive(research.contains(entry.getKey().research));
            entry.getValue().toggle.setOn(snapshot.systems().settings().enabled(entry.getKey()), false);
        }
    }
}
