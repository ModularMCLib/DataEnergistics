package com.fish_dan_.data_energistics.client.weapon.platform;

import com.fish_dan_.data_energistics.common.weapon.platform.model.PlatformAbility;
import com.fish_dan_.data_energistics.common.weapon.platform.ability.PlatformFlightMining;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.common.NeoForgeMod;

/** Keeps client block-breaking prediction aligned with the server's active flight ability. */
public final class PlatformFlightMiningClient {

    @SubscribeEvent
    public void breakSpeed(PlayerEvent.BreakSpeed event) {
        if (!(event.getEntity() instanceof LocalPlayer player) || player.getAbilities().instabuild || player.isSpectator()) return;
        boolean airborne = !player.onGround();
        boolean submerged = player.isEyeInFluidType(NeoForgeMod.WATER_TYPE.value());
        if (!airborne && !submerged) return;
        if ((PlatformClientState.snapshot.systems().active() & PlatformAbility.FLIGHT.flag) == 0) return;
        event.setNewSpeed(PlatformFlightMining.withoutMovementPenalties(event.getNewSpeed(), airborne, submerged,
                player.getAttributeValue(Attributes.SUBMERGED_MINING_SPEED)));
    }
}
