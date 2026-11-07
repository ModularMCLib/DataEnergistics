package com.fish_dan_.data_energistics.common.weapon.platform.ui;

import com.fish_dan_.data_energistics.common.research.item.ItemResearchUpgrades;
import com.fish_dan_.data_energistics.common.weapon.platform.model.PlatformAbility;
import com.fish_dan_.data_energistics.common.weapon.platform.model.ThermalPalette;
import com.fish_dan_.data_energistics.common.weapon.platform.network.PlatformRpc;
import com.fish_dan_.data_energistics.common.weapon.platform.protocol.PlatformSnapshot;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.networking.rpc.RPCPacketDistributor;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import org.jspecify.annotations.Nullable;

import java.util.EnumMap;

/** Player support, mobility and vision switches share the equipment menu's native toggle component. */
final class PlatformAbilitiesPanel extends UIElement {

    private final EnumMap<PlatformAbility, WeaponPlatformToggleRow> rows = new EnumMap<>(PlatformAbility.class);
    private final EnumMap<PlatformAbility, PlatformOptionGroup> groups = new EnumMap<>(PlatformAbility.class);
    private final Button palette = new Button();
    private final boolean vision;
    private final @Nullable PlatformLowLightPanel lowLight;
    private ThermalPalette selectedPalette = ThermalPalette.WHITE_HOT;

    PlatformAbilitiesPanel(Player player, PlatformSettingCategory category) {
        addClass("platform-shield-settings");
        vision = category == PlatformSettingCategory.SUPPORT;
        lowLight = vision ? new PlatformLowLightPanel(player) : null;
        PlatformAbility[] abilities = switch (category) {
            case DEFENSE -> new PlatformAbility[] { PlatformAbility.ARMOR, PlatformAbility.SUFFOCATION_PROTECTION, PlatformAbility.KEEP_INVENTORY };
            case MOBILITY -> new PlatformAbility[] { PlatformAbility.MOVEMENT_PROTECTION, PlatformAbility.NOCLIP };
            case SUPPORT -> new PlatformAbility[] { PlatformAbility.LOW_LIGHT_VISION, PlatformAbility.THERMAL_IMAGING, PlatformAbility.SONAR, PlatformAbility.NIGHT_VISION, PlatformAbility.SATIETY };
            default -> new PlatformAbility[0];
        };
        for (PlatformAbility ability : abilities) {
            var row = new WeaponPlatformToggleRow(Component.translatable("gui.data_energistics.weapon_platform.ability." + ability.id), on -> {
                if (player.level().isClientSide) RPCPacketDistributor.rpcToServer(PlatformRpc.REQUEST, PlatformRpc.ABILITY, 0, ability.ordinal());
            });
            row.addClass("platform-category-option");
            row.style(style -> style.tooltips(Component.translatable("research.data_energistics.biochip.specialized.weapon_platform." + ability.id + ".description")));
            rows.put(ability, row);
            PlatformOptionGroup group = new PlatformOptionGroup(row);
            if (ability == PlatformAbility.LOW_LIGHT_VISION && lowLight != null) {
                group.addOptions(lowLight);
                group.setOptionsVisible(false);
            }
            if (vision && ability == PlatformAbility.THERMAL_IMAGING) group.addOptions(palette);
            groups.put(ability, group);
            addChild(group);
        }
        if (vision) {
            palette.addClasses("platform-action", "platform-category-option");
            palette.setOnClick(event -> {
                if (player.level().isClientSide) RPCPacketDistributor.rpcToServer(PlatformRpc.REQUEST, PlatformRpc.THERMAL_PALETTE, 0,
                        (selectedPalette.ordinal() + 1) % ThermalPalette.values().length);
            });
        }
    }

    void bind(PlatformSnapshot snapshot) {
        var research = ItemResearchUpgrades.of(snapshot.chip()).ids();
        for (var entry : rows.entrySet()) {
            entry.getValue().setActive(entry.getKey().available(research));
            entry.getValue().toggle.setOn(snapshot.systems().settings().enabled(entry.getKey()), false);
        }
        if (!vision) return;
        if (lowLight != null) groups.get(PlatformAbility.LOW_LIGHT_VISION).setOptionsVisible(
                lowLight.bind(snapshot.systems().settings(), PlatformAbility.LOW_LIGHT_VISION.available(research)));
        selectedPalette = snapshot.systems().settings().thermalPalette();
        palette.setActive(PlatformAbility.THERMAL_IMAGING.available(research));
        palette.setText(Component.translatable("gui.data_energistics.weapon_platform.thermal_palette",
                Component.translatable("gui.data_energistics.weapon_platform.thermal_palette." + selectedPalette.getSerializedName())));
    }
}
