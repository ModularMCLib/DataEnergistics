package com.fish_dan_.data_energistics.common.weapon.platform.ui;

import com.fish_dan_.data_energistics.common.research.item.ItemResearchUpgrades;
import com.fish_dan_.data_energistics.common.weapon.platform.model.PlatformAbility;
import com.fish_dan_.data_energistics.common.weapon.platform.network.PlatformRpc;
import com.fish_dan_.data_energistics.common.weapon.platform.prospecting.ProspectingFilters;
import com.fish_dan_.data_energistics.common.weapon.platform.protocol.PlatformSnapshot;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.networking.rpc.RPCPacketDistributor;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/** Scan behavior is server-owned; HUD appearance remains in the existing client module editor. */
final class PlatformProspectingPanel extends UIElement {

    private final WeaponPlatformToggleRow enabled;
    private final TextField radius = new TextField();
    private final UIElement filters = new UIElement();
    private final Button add = new Button();
    private final Player player;
    private @Nullable ProspectingFilters displayed;

    PlatformProspectingPanel(Player player) {
        this.player = player;
        addClass("platform-shield-settings");
        enabled = new WeaponPlatformToggleRow(Component.translatable("gui.data_energistics.weapon_platform.ability.prospecting"), on -> {
            if (player.level().isClientSide) RPCPacketDistributor.rpcToServer(PlatformRpc.REQUEST, PlatformRpc.ABILITY, 0, PlatformAbility.PROSPECTING.ordinal());
        });
        enabled.addClass("platform-category-option");
        Label name = new Label();
        name.setValue(Component.translatable("gui.data_energistics.weapon_platform.scan_radius"));
        name.addClasses("platform-label", "platform-shield-status");
        radius.addClasses("platform-editor-input", "platform-shield-color");
        radius.addEventListener(UIEvents.KEY_DOWN, event -> {
            if (!radius.isActive() || event.keyCode != GLFW.GLFW_KEY_ENTER && event.keyCode != GLFW.GLFW_KEY_KP_ENTER) return;
            String value = radius.getValue();
            if (value.matches("[0-9]{1,3}")) {
                int amount = Integer.parseInt(value);
                if (amount >= 16 && amount <= 384 && player.level().isClientSide) RPCPacketDistributor.rpcToServer(PlatformRpc.REQUEST, PlatformRpc.SCAN_RADIUS, 0, amount);
            }
            event.stopPropagation();
        });
        filters.addClass("platform-prospecting-filters");
        add.setText(Component.translatable("gui.data_energistics.weapon_platform.prospecting_add"));
        add.addClasses("platform-action", "platform-category-option");
        add.setOnClick(event -> {
            if (player.level().isClientSide) RPCPacketDistributor.rpcToServer(PlatformRpc.ORE_FILTER, 0, 0, "");
        });
        addChildren(enabled, name, radius, filters, add);
    }

    void bind(PlatformSnapshot snapshot) {
        boolean researched = ItemResearchUpgrades.of(snapshot.chip()).ids().contains(PlatformAbility.PROSPECTING.research);
        enabled.setActive(researched);
        enabled.toggle.setOn(snapshot.systems().settings().enabled(PlatformAbility.PROSPECTING), false);
        radius.setActive(researched);
        if (!radius.isFocused()) radius.setValue(Integer.toString(snapshot.systems().settings().scanRadius()), false);
        var selected = snapshot.systems().prospecting();
        if (!selected.equals(displayed)) {
            displayed = selected;
            filters.clearAllChildren();
            for (int index = 0; index < selected.entries().size(); index++) filters.addChild(new ProspectingFilterRow(player, index, selected.entries().get(index)));
        }
        for (var row : filters.getChildren()) ((ProspectingFilterRow) row).available(researched);
        add.setActive(researched && selected.entries().size() < 64);
    }
}
