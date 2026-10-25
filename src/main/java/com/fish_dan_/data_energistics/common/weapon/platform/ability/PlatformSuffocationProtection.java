package com.fish_dan_.data_energistics.common.weapon.platform.ability;

import com.fish_dan_.data_energistics.common.weapon.platform.model.PlatformAbility;
import com.fish_dan_.data_energistics.common.weapon.platform.runtime.WeaponPlatformTicker;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageTypes;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityInvulnerabilityCheckEvent;

/** Paid platform immunity rejects suffocation before shield absorption and lethal damage handling. */
public final class PlatformSuffocationProtection {

    @SubscribeEvent
    public void check(EntityInvulnerabilityCheckEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(event.getSource().is(DamageTypes.DROWN) || event.getSource().is(DamageTypes.IN_WALL))) return;
        var runtime = WeaponPlatformTicker.runtime(player);
        if (runtime != null && (runtime.abilities.active & PlatformAbility.SUFFOCATION_PROTECTION.flag) != 0) event.setInvulnerable(true);
    }
}
