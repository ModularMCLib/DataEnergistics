package com.fish_dan_.data_energistics.common.weapon.platform.ui;

import com.fish_dan_.data_energistics.common.weapon.platform.network.PlatformRpc;
import com.fish_dan_.data_energistics.common.weapon.platform.protocol.PlatformSnapshot;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.networking.rpc.RPCPacketDistributor;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/** Server-backed target and ally-protection settings within the platform's combat categories. */
final class PlatformCombatSettingsPanel extends WeaponPlatformScrollView {

    private final PlatformSettingCategory category;
    private final WeaponPlatformToggleRow[] rows;

    PlatformCombatSettingsPanel(Player player, PlatformSettingCategory category) {
        this.category = category;
        addClass("platform-category-scroll");
        UIElement content = new UIElement();
        content.addClass("platform-category-content");
        String[] keys = switch (category) {
            case ATTACK -> new String[] { "targets.players", "targets.hostile", "targets.friendly", "targets.neutral", "targets.other" };
            case DEFENSE -> new String[] { "protection" };
            case HUD -> throw new IllegalArgumentException("HUD settings do not belong to a combat page");
        };
        rows = new WeaponPlatformToggleRow[keys.length];
        for (int index = 0; index < keys.length; index++) {
            int argument = 1 << index;
            WeaponPlatformToggleRow row = rows[index] = new WeaponPlatformToggleRow(Component.translatable(
                    "gui.data_energistics.weapon_platform." + keys[index]),
                    on -> {
                        if (player.level().isClientSide) RPCPacketDistributor.rpcToServer(PlatformRpc.REQUEST,
                                category == PlatformSettingCategory.ATTACK ? PlatformRpc.TARGETS : PlatformRpc.PROTECTION, 0, argument);
                    });
            row.addClass("platform-category-option");
            content.addChild(row);
        }
        addScrollViewChild(content);
        if (category == PlatformSettingCategory.DEFENSE) {
            Button reboot = new Button();
            reboot.setText(Component.translatable("effect.data_energistics.radix_reboot"));
            reboot.addClass("platform-action");
            reboot.addClass("platform-category-option");
            reboot.setOnClick(event -> {
                if (player.level().isClientSide) RPCPacketDistributor.rpcToServer(PlatformRpc.REQUEST, PlatformRpc.REBOOT, 0, 0);
            });
            content.addChild(reboot);
        }
    }

    void bind(PlatformSnapshot snapshot) {
        for (int index = 0; index < rows.length; index++) rows[index].toggle.setOn(
                category == PlatformSettingCategory.ATTACK ? (snapshot.targetMask() & 1 << index) != 0 : snapshot.friendlyProtection(), false);
    }
}
