package com.fish_dan_.data_energistics.common.event;

import com.fish_dan_.data_energistics.common.blockentity.machine.DataMimeticFieldBlockEntity;
import com.fish_dan_.data_energistics.common.command.DataEnergisticsCommands;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.governor.TrinityServerTickMetrics;
import com.fish_dan_.data_energistics.common.effect.ChromaticGlow;
import com.fish_dan_.data_energistics.common.effect.RadixStasisAiLogic;
import com.fish_dan_.data_energistics.common.effect.WeaponBurn;
import com.fish_dan_.data_energistics.common.effect.reboot.RadixReboot;
import com.fish_dan_.data_energistics.common.item.powered.PersistentFarmlandLogic;
import com.fish_dan_.data_energistics.common.item.powered.sanctifier.SanctifierCarrySync;
import com.fish_dan_.data_energistics.common.item.powered.sanctifier.SanctifierCombat;
import com.fish_dan_.data_energistics.common.orbital.attack.OrbitalAttackTicker;
import com.fish_dan_.data_energistics.common.orbital.attack.OrbitalAttackVisualTicker;
import com.fish_dan_.data_energistics.common.orbital.control.OrbitalControlHudTicker;
import com.fish_dan_.data_energistics.common.orbital.control.OrbitalControlPreviewTicker;
import com.fish_dan_.data_energistics.common.orbital.endpoint.OrbitalEndpointChunkTickets;
import com.fish_dan_.data_energistics.common.orbital.map.OrbitalTacticalMapCoordinator;
import com.fish_dan_.data_energistics.common.orbital.reserve.OrbitalReserveTicker;
import com.fish_dan_.data_energistics.common.recipe.RecipeReloadEventHandler;
import com.fish_dan_.data_energistics.common.recipe.containmentsphere.RadixContainmentSphereRightClickRecipeLogic;
import com.fish_dan_.data_energistics.common.recipe.timeshift.TimeShiftTransformLogic;
import com.fish_dan_.data_energistics.common.research.lifecycle.ResearchEvents;
import com.fish_dan_.data_energistics.common.terminal.player.session.TerminalSessions;
import com.fish_dan_.data_energistics.common.tick.ServerTickDelayQueue;
import com.fish_dan_.data_energistics.common.weapon.platform.combat.GunImpactEffects;
import com.fish_dan_.data_energistics.common.weapon.platform.combat.PlasmaArcTicker;
import com.fish_dan_.data_energistics.common.weapon.platform.runtime.WeaponPlatformTicker;
import com.fish_dan_.data_energistics.common.world.meteorite.DataMeteoriteCompassTargetInvalidation;
import com.fish_dan_.data_energistics.common.world.packaged.PackagedRecoveryTicker;
import com.fish_dan_.data_energistics.common.world.sanctum.DataSanctumPortalLogic;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;

public final class CommonEventRegistrar {

    private CommonEventRegistrar() {}

    public static void register() {
        NeoForge.EVENT_BUS.register(new GunImpactEffects());
        NeoForge.EVENT_BUS.register(new PlasmaArcTicker());
        NeoForge.EVENT_BUS.register(new WeaponPlatformTicker());
        NeoForge.EVENT_BUS.register(new ServerLifecycleEventHandler());
        NeoForge.EVENT_BUS.register(new ResearchEvents());
        NeoForge.EVENT_BUS.register(new TerminalSessions());
        NeoForge.EVENT_BUS.register(new PackagedRecoveryTicker());
        NeoForge.EVENT_BUS.register(new OrbitalEndpointChunkTickets());
        NeoForge.EVENT_BUS.register(new OrbitalReserveTicker());
        NeoForge.EVENT_BUS.register(new OrbitalAttackTicker());
        NeoForge.EVENT_BUS.register(new OrbitalAttackVisualTicker());
        NeoForge.EVENT_BUS.register(new DataEnergisticsCommands());
        NeoForge.EVENT_BUS.register(OrbitalTacticalMapCoordinator.INSTANCE);
        NeoForge.EVENT_BUS.register(new OrbitalControlHudTicker());
        NeoForge.EVENT_BUS.register(new OrbitalControlPreviewTicker());
        NeoForge.EVENT_BUS.register(new PoweredToolAttributeModifierHandler());
        NeoForge.EVENT_BUS.register(new TimeShiftTransformLogic());
        NeoForge.EVENT_BUS.register(new RadixContainmentSphereRightClickRecipeLogic());
        NeoForge.EVENT_BUS.register(new RadixStasisAiLogic());
        NeoForge.EVENT_BUS.register(new RadixReboot());
        NeoForge.EVENT_BUS.register(new WeaponBurn());
        NeoForge.EVENT_BUS.register(new ChromaticGlow());
        NeoForge.EVENT_BUS.register(new PersistentFarmlandLogic());
        NeoForge.EVENT_BUS.register(new SanctifierCombat());
        NeoForge.EVENT_BUS.register(new SanctifierCarrySync());
        NeoForge.EVENT_BUS.register(new RecipeReloadEventHandler());
        NeoForge.EVENT_BUS.register(new DataMeteoriteCompassTargetInvalidation());
        NeoForge.EVENT_BUS.register(new DataSanctumPortalLogic());
        NeoForge.EVENT_BUS.register(new ServerTickDelayQueue());
        NeoForge.EVENT_BUS.register(new TrinityServerTickMetrics());
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, DataMimeticFieldBlockEntity::captureSimulatedSpawnedDrops);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, DataMimeticFieldBlockEntity::captureSimulatedDeathDrops);
    }
}
