package com.fish_dan_.data_energistics.common.weapon.platform.ui;

import com.fish_dan_.data_energistics.common.weapon.platform.model.PlatformAbility;
import com.fish_dan_.data_energistics.common.weapon.platform.network.PlatformRpc;
import com.fish_dan_.data_energistics.common.weapon.platform.storage.LowLightVision;
import com.fish_dan_.data_energistics.common.weapon.platform.storage.PlatformAbilities;

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
        gain = new PlatformPercentageSlider("gui.data_energistics.weapon_platform.low_light.gain", LowLightVision.MIN_GAIN, LowLightVision.MAX_GAIN, 100,
                value -> requestValue(player, 0, value));
        automatic = new WeaponPlatformToggleRow(Component.translatable("gui.data_energistics.weapon_platform.low_light.automatic_brightness"), on -> request(player, 1, on ? 1 : 0));
        automatic.addClass("platform-category-option");
        automatic.style(style -> style.tooltips(Component.translatable("gui.data_energistics.weapon_platform.low_light.automatic_brightness.description")));
        noise = new PlatformPercentageSlider("gui.data_energistics.weapon_platform.low_light.noise", LowLightVision.MIN_NOISE, LowLightVision.MAX_NOISE, 1,
                value -> requestValue(player, 2, value));
        infrared = new WeaponPlatformToggleRow(Component.translatable("gui.data_energistics.weapon_platform.low_light.infrared_illuminator"), on -> request(player, 3, on ? 1 : 0));
        infrared.addClass("platform-category-option");
        infrared.style(style -> style.tooltips(Component.translatable("gui.data_energistics.weapon_platform.low_light.infrared_illuminator.description")));
        addChildren(gain, automatic, noise, infrared);
    }

    private static void request(Player player, int field, int value) {
        if (player.level().isClientSide) RPCPacketDistributor.rpcToServer(PlatformRpc.REQUEST, PlatformRpc.LOW_LIGHT, field, value);
    }

    private static void requestValue(Player player, int field, float value) {
        // Preserve the continuous value within the existing integer-argument request format.
        request(player, field, Float.floatToIntBits(value));
    }

    public void bind(PlatformAbilities settings, boolean researched) {
        setDisplay(researched && settings.enabled(PlatformAbility.LOW_LIGHT_VISION));
        LowLightVision selected = settings.lowLight();
        gain.bind(selected.gain(), researched);
        automatic.toggle.setOn(selected.automaticBrightness(), false);
        noise.bind(selected.noise(), researched);
        infrared.toggle.setOn(selected.infraredIlluminator(), false);
    }
}
