package com.fish_dan_.data_energistics.common.weapon.platform.ui;

import com.fish_dan_.data_energistics.common.research.item.ItemResearchUpgrades;
import com.fish_dan_.data_energistics.common.weapon.platform.model.PlatformChipUpgrades;
import com.fish_dan_.data_energistics.common.weapon.platform.network.PlatformRpc;
import com.fish_dan_.data_energistics.common.weapon.platform.protocol.PlatformSnapshot;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.networking.rpc.RPCPacketDistributor;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/** Research-gated protection defaults to the platform; extra groups remain independent user choices. */
public final class PlatformEquipmentProtectionPanel extends UIElement {

    private final WeaponPlatformToggleRow[] rows = new WeaponPlatformToggleRow[5];

    public PlatformEquipmentProtectionPanel(Player player) {
        addClass("platform-shield-settings");
        PlatformOptionGroup group = new PlatformOptionGroup(rows[0] = new WeaponPlatformToggleRow(
                Component.translatable("gui.data_energistics.weapon_platform.disarm.enabled"), on -> request(player, 0, on)));
        rows[0].addClass("platform-category-option");
        String[] keys = { "enabled", "armor", "curios", "main_hand", "off_hand" };
        for (int index = 1; index < rows.length; index++) {
            int field = index;
            var row = rows[index] = new WeaponPlatformToggleRow(Component.translatable("gui.data_energistics.weapon_platform.disarm." + keys[index]), on -> request(player, field, on));
            row.addClass("platform-category-option");
            group.addOptions(row);
        }
        addChild(group);
        style(style -> style.tooltips(Component.translatable("research.data_energistics.biochip.specialized.weapon_platform.disarm_protection.description")));
    }

    private static void request(Player player, int field, boolean enabled) {
        if (player.level().isClientSide) RPCPacketDistributor.rpcToServer(PlatformRpc.REQUEST, PlatformRpc.DISARM_PROTECTION, field, enabled ? 1 : 0);
    }

    public void bind(PlatformSnapshot snapshot) {
        boolean researched = ItemResearchUpgrades.of(snapshot.chip()).ids().contains(PlatformChipUpgrades.DISARM_PROTECTION);
        setDisplay(researched);
        var settings = snapshot.systems().settings().equipmentProtection();
        boolean[] values = { settings.enabled(), settings.armor(), settings.curios(), settings.mainHand(), settings.offHand() };
        for (int index = 0; index < rows.length; index++) {
            rows[index].setActive(researched && (index == 0 || settings.enabled()));
            rows[index].toggle.setOn(values[index], false);
        }
    }
}
