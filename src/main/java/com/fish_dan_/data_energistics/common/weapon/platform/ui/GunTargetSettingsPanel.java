package com.fish_dan_.data_energistics.common.weapon.platform.ui;

import com.fish_dan_.data_energistics.common.weapon.platform.model.GunForm;
import com.fish_dan_.data_energistics.common.weapon.platform.network.PlatformRpc;
import com.fish_dan_.data_energistics.common.weapon.platform.protocol.GunSnapshot;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.networking.rpc.RPCPacketDistributor;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import org.jspecify.annotations.Nullable;

import java.util.function.IntSupplier;

/** Item-owned automatic targeting settings in the unused left side of an attack gun's details. */
final class GunTargetSettingsPanel extends UIElement {

    private final WeaponPlatformToggleRow[] rows = new WeaponPlatformToggleRow[6];

    GunTargetSettingsPanel(Player player, IntSupplier selected) {
        addClass("platform-gun-target-settings");
        String[] keys = { "inherit_targets", "targets.players", "targets.hostile", "targets.friendly", "targets.neutral", "targets.other" };
        for (int index = 0; index < rows.length; index++) {
            int operation = index == 0 ? PlatformRpc.GUN_INHERIT_TARGETS : PlatformRpc.GUN_TARGETS;
            int argument = index == 0 ? 0 : 1 << (index - 1);
            var row = rows[index] = new WeaponPlatformToggleRow(Component.translatable("gui.data_energistics.weapon_platform." + keys[index]), on -> {
                if (player.level().isClientSide) RPCPacketDistributor.rpcToServer(PlatformRpc.REQUEST, operation, selected.getAsInt(), argument);
            });
            row.addClass("platform-gun-target-option");
            addChild(row);
        }
    }

    void bind(@Nullable GunSnapshot gun, int inheritedMask) {
        setDisplay(gun != null && GunForm.of(gun.item()) != GunForm.MEDICAL);
        if (gun == null) return;
        var settings = gun.settings();
        rows[0].toggle.setOn(settings.inheritTargets(), false);
        int mask = settings.inheritTargets() ? inheritedMask : settings.targetMask();
        for (int index = 1; index < rows.length; index++) {
            rows[index].setActive(!settings.inheritTargets());
            rows[index].toggle.setOn((mask & 1 << (index - 1)) != 0, false);
        }
    }
}
