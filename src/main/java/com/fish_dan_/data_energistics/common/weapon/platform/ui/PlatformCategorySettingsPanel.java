package com.fish_dan_.data_energistics.common.weapon.platform.ui;

import com.fish_dan_.data_energistics.common.research.item.ItemResearchUpgrades;
import com.fish_dan_.data_energistics.common.weapon.platform.model.PlatformChipUpgrades;
import com.fish_dan_.data_energistics.common.weapon.platform.network.PlatformRpc;
import com.fish_dan_.data_energistics.common.weapon.platform.protocol.PlatformSnapshot;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.networking.rpc.RPCPacketDistributor;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import org.jspecify.annotations.Nullable;

/** Server-backed settings grouped by combat, mobility, support and prospecting responsibilities. */
final class PlatformCategorySettingsPanel extends WeaponPlatformScrollView {

    private final PlatformSettingCategory category;
    private final WeaponPlatformToggleRow[] rows;
    private final @Nullable PlatformShieldPanel shield;
    private final @Nullable PlatformAbilitiesPanel abilities;
    private final @Nullable PlatformProspectingPanel prospecting;
    private final @Nullable PlatformDomainPanel domain;
    private final @Nullable PlatformFlightPanel flight;
    private final @Nullable PlatformEffectSettingsPanel effects;
    private final Button reboot = new Button();

    PlatformCategorySettingsPanel(Player player, PlatformSettingCategory category) {
        this.category = category;
        addClass("platform-category-scroll");
        UIElement content = new UIElement();
        content.addClass("platform-category-content");
        String[] keys = switch (category) {
            case ATTACK -> new String[] { "targets.players", "targets.hostile", "targets.friendly", "targets.neutral", "targets.other" };
            case DEFENSE -> new String[] { "protection" };
            case MOBILITY, SUPPORT, PROSPECTING, EFFECTS -> new String[0];
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
        if (category == PlatformSettingCategory.EFFECTS) {
            verticalContainer.setDisplay(false);
        } else {
            addScrollViewChild(content);
        }
        shield = category == PlatformSettingCategory.DEFENSE ? new PlatformShieldPanel(player) : null;
        if (shield != null) content.addChild(shield);
        flight = category == PlatformSettingCategory.MOBILITY ? new PlatformFlightPanel(player) : null;
        if (flight != null) content.addChild(flight);
        abilities = category == PlatformSettingCategory.DEFENSE || category == PlatformSettingCategory.MOBILITY || category == PlatformSettingCategory.SUPPORT ? new PlatformAbilitiesPanel(player, category) : null;
        if (abilities != null) content.addChild(abilities);
        prospecting = category == PlatformSettingCategory.PROSPECTING ? new PlatformProspectingPanel(player) : null;
        if (prospecting != null) content.addChild(prospecting);
        effects = category == PlatformSettingCategory.EFFECTS ? new PlatformEffectSettingsPanel(player) : null;
        if (effects != null) addChild(effects);
        domain = category == PlatformSettingCategory.ATTACK ? new PlatformDomainPanel(player) : null;
        if (domain != null) content.addChild(domain);
        if (category == PlatformSettingCategory.DEFENSE) {
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
        if (shield != null) shield.bind(snapshot);
        if (abilities != null) abilities.bind(snapshot);
        if (prospecting != null) prospecting.bind(snapshot);
        if (effects != null) effects.bind(snapshot);
        if (domain != null) domain.bind(snapshot);
        if (flight != null) flight.bind(snapshot);
        reboot.setActive(ItemResearchUpgrades.of(snapshot.chip()).ids().contains(PlatformChipUpgrades.REBOOT) && snapshot.systems().rebootCooldown() == 0);
        for (int index = 0; index < rows.length; index++) rows[index].toggle.setOn(
                category == PlatformSettingCategory.ATTACK ? (snapshot.targetMask() & 1 << index) != 0 : snapshot.friendlyProtection(), false);
    }
}
