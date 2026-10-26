package com.fish_dan_.data_energistics.common.weapon.platform.ability;

import com.fish_dan_.data_energistics.common.weapon.platform.model.PlatformAbility;
import com.fish_dan_.data_energistics.common.weapon.platform.runtime.PlatformRuntime;
import com.fish_dan_.data_energistics.common.weapon.platform.runtime.WeaponPlatformTicker;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.common.NeoForgeMod;

import net.neoforged.bus.api.SubscribeEvent;

/** Restores only the airborne block-breaking penalty while platform flight is powered and active. */
public final class PlatformFlightMining {

    @SubscribeEvent
    public void breakSpeed(PlayerEvent.BreakSpeed event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.getAbilities().instabuild || player.isSpectator()) return;
        boolean airborne = !player.onGround();
        boolean submerged = player.isEyeInFluidType(NeoForgeMod.WATER_TYPE.value());
        if (!airborne && !submerged) return;
        PlatformRuntime runtime = WeaponPlatformTicker.runtime(player);
        if (runtime == null || (runtime.abilities.active & PlatformAbility.FLIGHT.flag) == 0) return;
        event.setNewSpeed(withoutMovementPenalties(event.getNewSpeed(), airborne, submerged,
                player.getAttributeValue(Attributes.SUBMERGED_MINING_SPEED)));
    }

    public static float withoutMovementPenalties(float speed, boolean airborne, boolean submerged, double submergedMiningSpeed) {
        if (airborne) speed *= 5.0F;
        if (submerged && submergedMiningSpeed > 0 && submergedMiningSpeed < 1.0) speed /= (float) submergedMiningSpeed;
        return speed;
    }
}
