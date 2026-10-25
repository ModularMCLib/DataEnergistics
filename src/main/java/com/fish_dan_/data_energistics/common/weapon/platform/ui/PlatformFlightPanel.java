package com.fish_dan_.data_energistics.common.weapon.platform.ui;

import com.fish_dan_.data_energistics.common.research.item.ItemResearchUpgrades;
import com.fish_dan_.data_energistics.common.weapon.platform.model.PlatformAbility;
import com.fish_dan_.data_energistics.common.weapon.platform.network.PlatformRpc;
import com.fish_dan_.data_energistics.common.weapon.platform.protocol.PlatformSnapshot;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.networking.rpc.RPCPacketDistributor;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import org.lwjgl.glfw.GLFW;

import java.util.Locale;

final class PlatformFlightPanel extends UIElement {

    private final TextField maximum = new TextField();

    PlatformFlightPanel(Player player) {
        addClass("platform-shield-settings");
        Label name = new Label();
        name.setValue(Component.translatable("gui.data_energistics.weapon_platform.maximum_flight_speed"));
        name.addClasses("platform-label", "platform-shield-status");
        maximum.addClasses("platform-editor-input", "platform-shield-color");
        maximum.addEventListener(UIEvents.KEY_DOWN, event -> {
            if (!maximum.isActive() || event.keyCode != GLFW.GLFW_KEY_ENTER && event.keyCode != GLFW.GLFW_KEY_KP_ENTER) return;
            String value = maximum.getValue();
            if (value.matches("[0-9]{1,3}(\\.[0-9]{1,2})?")) {
                double speed = Double.parseDouble(value);
                if (speed >= 5.6 && speed <= 100 && player.level().isClientSide) RPCPacketDistributor.rpcToServer(PlatformRpc.REQUEST, PlatformRpc.FLIGHT_SPEED, 0, (int) Math.round(speed * 100));
            }
            event.stopPropagation();
        });
        addChildren(name, maximum);
    }

    void bind(PlatformSnapshot snapshot) {
        maximum.setActive(ItemResearchUpgrades.of(snapshot.chip()).ids().contains(PlatformAbility.FLIGHT.research));
        if (!maximum.isFocused()) maximum.setValue(String.format(Locale.ROOT, "%.2f", snapshot.systems().settings().maximumFlightSpeed() * 20), false);
    }
}
