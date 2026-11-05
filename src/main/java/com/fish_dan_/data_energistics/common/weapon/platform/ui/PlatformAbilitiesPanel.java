package com.fish_dan_.data_energistics.common.weapon.platform.ui;

import com.fish_dan_.data_energistics.common.research.item.ItemResearchUpgrades;
import com.fish_dan_.data_energistics.common.weapon.platform.model.PlatformAbility;
import com.fish_dan_.data_energistics.common.weapon.platform.model.ThermalPalette;
import com.fish_dan_.data_energistics.common.weapon.platform.network.PlatformRpc;
import com.fish_dan_.data_energistics.common.weapon.platform.protocol.PlatformSnapshot;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.networking.rpc.RPCPacketDistributor;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import java.util.EnumMap;

/** Player support, mobility and vision switches share the equipment menu's native toggle component. */
final class PlatformAbilitiesPanel extends UIElement {

    private final EnumMap<PlatformAbility, WeaponPlatformToggleRow> rows = new EnumMap<>(PlatformAbility.class);
    private final Button palette = new Button();
    private final Label paletteName = new Label();
    private final Label visionStatus = new Label();
    private final boolean vision;
    private ThermalPalette selectedPalette = ThermalPalette.WHITE_HOT;

    PlatformAbilitiesPanel(Player player, PlatformSettingCategory category) {
        addClass("platform-shield-settings");
        vision = category == PlatformSettingCategory.SUPPORT;
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
            addChild(row);
        }
        if (vision) {
            palette.noText();
            palette.addClasses("platform-action", "platform-category-option");
            paletteName.addClass("platform-label");
            paletteName.setAllowHitTest(false);
            palette.addChild(paletteName);
            palette.setOnClick(event -> {
                if (player.level().isClientSide) RPCPacketDistributor.rpcToServer(PlatformRpc.REQUEST, PlatformRpc.THERMAL_PALETTE, 0,
                        (selectedPalette.ordinal() + 1) % ThermalPalette.values().length);
            });
            visionStatus.addClasses("platform-label", "platform-shield-status");
            addChildren(palette, visionStatus);
        }
    }

    void bind(PlatformSnapshot snapshot) {
        var research = ItemResearchUpgrades.of(snapshot.chip()).ids();
        for (var entry : rows.entrySet()) {
            entry.getValue().setActive(entry.getKey().available(research));
            entry.getValue().toggle.setOn(snapshot.systems().settings().enabled(entry.getKey()), false);
        }
        if (!vision) return;
        selectedPalette = snapshot.systems().settings().thermalPalette();
        palette.setActive(PlatformAbility.THERMAL_IMAGING.available(research));
        paletteName.setValue(Component.translatable("gui.data_energistics.weapon_platform.thermal_palette",
                Component.translatable("gui.data_energistics.weapon_platform.thermal_palette." + selectedPalette.getSerializedName())));
        boolean covered = snapshot.systems().settings().enabled(PlatformAbility.LOW_LIGHT_VISION) && (snapshot.systems().active() & PlatformAbility.NIGHT_VISION.flag) != 0;
        visionStatus.setValue(Component.translatable(covered ? "gui.data_energistics.weapon_platform.low_light_covered" : "gui.data_energistics.weapon_platform.night_vision_prerequisites"));
    }
}
