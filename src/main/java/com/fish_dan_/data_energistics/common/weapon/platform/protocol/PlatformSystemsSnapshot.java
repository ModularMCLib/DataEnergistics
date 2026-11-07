package com.fish_dan_.data_energistics.common.weapon.platform.protocol;

import com.fish_dan_.data_energistics.common.data.DEDataComponents;
import com.fish_dan_.data_energistics.common.weapon.platform.domain.StasisDomainSavedData;
import com.fish_dan_.data_energistics.common.weapon.platform.model.PlatformAbility;
import com.fish_dan_.data_energistics.common.weapon.platform.prospecting.ProspectingFilters;
import com.fish_dan_.data_energistics.common.weapon.platform.runtime.PlatformRuntime;
import com.fish_dan_.data_energistics.common.weapon.platform.shield.ShieldAppearance;
import com.fish_dan_.data_energistics.common.weapon.platform.shield.ShieldState;
import com.fish_dan_.data_energistics.common.weapon.platform.shield.ShieldUpgrades;
import com.fish_dan_.data_energistics.common.weapon.platform.storage.PlatformAbilities;
import com.fish_dan_.data_energistics.common.weapon.platform.storage.PlatformHudDisplay;
import com.fish_dan_.data_energistics.common.weapon.platform.storage.WeaponPlatformInventory;
import com.fish_dan_.data_energistics.common.world.reboot.RebootCooldownSavedData;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record PlatformSystemsSnapshot(PlatformAbilities settings, int active, ShieldState shield, ShieldUpgrades shieldUpgrades, int rebootCooldown, int domainRemaining, int domainCooldown, ProspectingFilters prospecting, PlatformHudDisplay hud, ShieldAppearance shieldAppearance) {

    public static final PlatformSystemsSnapshot EMPTY = new PlatformSystemsSnapshot(PlatformAbilities.DEFAULT, 0, ShieldState.EMPTY, ShieldUpgrades.BASE, 0, 0, 0, ProspectingFilters.DEFAULT, PlatformHudDisplay.DEFAULT, ShieldAppearance.DEFAULT);
    public static final Codec<PlatformSystemsSnapshot> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            PlatformAbilities.CODEC.fieldOf("settings").forGetter(PlatformSystemsSnapshot::settings),
            PlatformAbility.FLAGS_CODEC.fieldOf("active").forGetter(PlatformSystemsSnapshot::active),
            ShieldState.CODEC.fieldOf("shield").forGetter(PlatformSystemsSnapshot::shield),
            ShieldUpgrades.CODEC.fieldOf("shield_upgrades").forGetter(PlatformSystemsSnapshot::shieldUpgrades),
            Codec.intRange(0, 6000).fieldOf("reboot_cooldown").forGetter(PlatformSystemsSnapshot::rebootCooldown),
            Codec.intRange(0, 1200).fieldOf("domain_remaining").forGetter(PlatformSystemsSnapshot::domainRemaining),
            Codec.intRange(0, 4000).fieldOf("domain_cooldown").forGetter(PlatformSystemsSnapshot::domainCooldown),
            ProspectingFilters.CODEC.fieldOf("prospecting").forGetter(PlatformSystemsSnapshot::prospecting),
            PlatformHudDisplay.CODEC.fieldOf("hud").forGetter(PlatformSystemsSnapshot::hud),
            ShieldAppearance.CODEC.fieldOf("shield_appearance").forGetter(PlatformSystemsSnapshot::shieldAppearance)).apply(instance, PlatformSystemsSnapshot::new));

    public static PlatformSystemsSnapshot capture(PlatformRuntime runtime) {
        return new PlatformSystemsSnapshot(runtime.platform.getOrDefault(DEDataComponents.PLATFORM_ABILITIES, PlatformAbilities.DEFAULT),
                runtime.abilities.active | (runtime.platform.getOrDefault(DEDataComponents.PLATFORM_SHIELD, ShieldState.EMPTY).active() ? PlatformAbility.SHIELD.flag : 0),
                runtime.platform.getOrDefault(DEDataComponents.PLATFORM_SHIELD, ShieldState.EMPTY),
                ShieldUpgrades.of(runtime.inventory.getStackInSlot(WeaponPlatformInventory.CHIP_SLOT)),
                RebootCooldownSavedData.get(runtime.player.serverLevel().getServer()).remaining(runtime.player.serverLevel().getServer(), runtime.player.getUUID()),
                StasisDomainSavedData.get(runtime.player.serverLevel().getServer()).remaining(runtime.player.serverLevel().getServer(), runtime.player.getUUID()),
                StasisDomainSavedData.get(runtime.player.serverLevel().getServer()).cooldown(runtime.player.serverLevel().getServer(), runtime.player.getUUID()),
                runtime.platform.getOrDefault(DEDataComponents.PLATFORM_PROSPECTING, ProspectingFilters.DEFAULT),
                runtime.platform.getOrDefault(DEDataComponents.PLATFORM_HUD_SETTINGS, PlatformHudDisplay.DEFAULT),
                runtime.platform.getOrDefault(DEDataComponents.PLATFORM_SHIELD_APPEARANCE, ShieldAppearance.DEFAULT));
    }
}
