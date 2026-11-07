package com.fish_dan_.data_energistics.common.weapon.platform.ui;

import com.fish_dan_.data_energistics.common.weapon.platform.model.PlatformAbility;
import com.fish_dan_.data_energistics.common.weapon.platform.network.PlatformRpc;
import com.fish_dan_.data_energistics.common.weapon.platform.storage.LowLightSettings;
import com.fish_dan_.data_energistics.common.weapon.platform.storage.PlatformAbilitySettings;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.networking.rpc.RPCPacketDistributor;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/** Image-intensifier controls expand directly below the requested low-light switch. */
public final class PlatformLowLightPanel extends UIElement {

    private final PlatformPercentageSlider gain;
    private final PlatformPercentageSlider noise;
    private final WeaponPlatformToggleRow automatic;
    private final WeaponPlatformToggleRow infrared;

    public PlatformLowLightPanel(Player player) {
        addClass("platform-shield-settings");
        setDisplay(false);
        gain = new PlatformPercentageSlider("gui.data_energistics.weapon_platform.low_light.gain", 50, 800,
                value -> request(player, 0, value));
        automatic = new WeaponPlatformToggleRow(Component.translatable("gui.data_energistics.weapon_platform.low_light.automatic_brightness"), on -> request(player, 1, on ? 1 : 0));
        automatic.addClass("platform-category-option");
        automatic.style(style -> style.tooltips(Component.translatable("gui.data_energistics.weapon_platform.low_light.automatic_brightness.description")));
        noise = new PlatformPercentageSlider("gui.data_energistics.weapon_platform.low_light.noise", 0, 100,
                value -> request(player, 2, value));
        infrared = new WeaponPlatformToggleRow(Component.translatable("gui.data_energistics.weapon_platform.low_light.infrared_illuminator"), on -> request(player, 3, on ? 1 : 0));
        infrared.addClass("platform-category-option");
        infrared.style(style -> style.tooltips(Component.translatable("gui.data_energistics.weapon_platform.low_light.infrared_illuminator.description")));
        addChildren(gain, automatic, noise, infrared);
    }

    private static void request(Player player, int field, int value) {
        if (player.level().isClientSide) RPCPacketDistributor.rpcToServer(PlatformRpc.REQUEST, PlatformRpc.LOW_LIGHT, field, value);
    }

    public void bind(PlatformAbilitySettings settings, boolean researched) {
        setDisplay(researched && settings.enabled(PlatformAbility.LOW_LIGHT_VISION));
        LowLightSettings selected = settings.lowLight();
        gain.bind(Math.round(selected.gain() * 100), researched);
        automatic.toggle.setOn(selected.automaticBrightness(), false);
        noise.bind(selected.noise(), researched);
        infrared.toggle.setOn(selected.infraredIlluminator(), false);
    }
}
