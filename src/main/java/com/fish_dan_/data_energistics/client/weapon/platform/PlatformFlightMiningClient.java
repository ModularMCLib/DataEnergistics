package com.fish_dan_.data_energistics.client.weapon.platform;

import com.fish_dan_.data_energistics.common.data.DEAttachments;
import com.fish_dan_.data_energistics.common.research.item.ItemResearchUpgrades;
import com.fish_dan_.data_energistics.common.weapon.platform.model.PlatformAbility;

import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Keeps the shared mining formula aligned with research on the currently equipped platform. */
public final class PlatformFlightMiningClient {

    @SubscribeEvent
    public void tick(ClientTickEvent.Pre event) {
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        var snapshot = PlatformClientState.snapshot;
        player.getData(DEAttachments.PLATFORM_MOTION).miningPenaltyImmunity = snapshot.present() && ItemResearchUpgrades.of(snapshot.chip()).ids().contains(PlatformAbility.FLIGHT.research);
    }
}
