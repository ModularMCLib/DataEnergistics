package com.fish_dan_.data_energistics.common.weapon.platform.ui;

import com.fish_dan_.data_energistics.common.weapon.platform.model.PlatformAbility;
import com.fish_dan_.data_energistics.common.weapon.platform.network.PlatformRpc;
import com.fish_dan_.data_energistics.common.weapon.platform.storage.LowLightSettings;
import com.fish_dan_.data_energistics.common.weapon.platform.storage.PlatformAbilitySettings;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.networking.rpc.RPCPacketDistributor;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/** Image-intensifier controls expand directly below the requested low-light switch. */
final class PlatformLowLightPanel extends UIElement {

    private final Button gain = new Button();
    private final Button noise = new Button();
    private final WeaponPlatformToggleRow automatic;
    private final WeaponPlatformToggleRow infrared;
    private LowLightSettings selected = LowLightSettings.DEFAULT;

    PlatformLowLightPanel(Player player) {
        addClass("platform-shield-settings");
        setDisplay(false);
        gain.addClasses("platform-action", "platform-category-option");
        gain.setOnClick(event -> request(player, 0, Math.round((selected.gain() >= 8 ? 0.5f : Math.min(8, selected.gain() * 2)) * 100)));
        gain.style(style -> style.tooltips(Component.translatable("gui.data_energistics.weapon_platform.low_light.gain.description")));
        automatic = new WeaponPlatformToggleRow(Component.translatable("gui.data_energistics.weapon_platform.low_light.automatic_brightness"), on -> request(player, 1, on ? 1 : 0));
        automatic.addClass("platform-category-option");
        automatic.style(style -> style.tooltips(Component.translatable("gui.data_energistics.weapon_platform.low_light.automatic_brightness.description")));
        noise.addClasses("platform-action", "platform-category-option");
        noise.setOnClick(event -> request(player, 2, selected.noise() >= 100 ? 0 : (selected.noise() / 20 + 1) * 20));
        noise.style(style -> style.tooltips(Component.translatable("gui.data_energistics.weapon_platform.low_light.noise.description")));
        infrared = new WeaponPlatformToggleRow(Component.translatable("gui.data_energistics.weapon_platform.low_light.infrared_illuminator"), on -> request(player, 3, on ? 1 : 0));
        infrared.addClass("platform-category-option");
        infrared.style(style -> style.tooltips(Component.translatable("gui.data_energistics.weapon_platform.low_light.infrared_illuminator.description")));
        addChildren(gain, automatic, noise, infrared);
    }

    private static void request(Player player, int field, int value) {
        if (player.level().isClientSide) RPCPacketDistributor.rpcToServer(PlatformRpc.REQUEST, PlatformRpc.LOW_LIGHT, field, value);
    }

    void bind(PlatformAbilitySettings settings, boolean researched) {
        setDisplay(researched && settings.enabled(PlatformAbility.LOW_LIGHT_VISION));
        selected = settings.lowLight();
        gain.setText(Component.translatable("gui.data_energistics.weapon_platform.low_light.gain", Math.round(selected.gain() * 100)));
        automatic.toggle.setOn(selected.automaticBrightness(), false);
        noise.setText(Component.translatable("gui.data_energistics.weapon_platform.low_light.noise", selected.noise()));
        infrared.toggle.setOn(selected.infraredIlluminator(), false);
    }
}
