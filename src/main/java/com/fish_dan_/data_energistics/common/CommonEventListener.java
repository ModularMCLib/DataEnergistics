package com.fish_dan_.data_energistics.common;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.blockentity.machine.DataMimeticFieldBlockEntity;
import com.fish_dan_.data_energistics.common.blockentity.machine.DataTeleportAnchorBlockEntity;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.async.lifecycle.TrinityDispatchProposalLifecycle;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.gateway.TrinityPlanningGatewayLifecycle;
import com.fish_dan_.data_energistics.common.data.DEVerticalMultiBlocks;
import com.fish_dan_.data_energistics.common.effect.RadixStasisAiLogic;
import com.fish_dan_.data_energistics.common.effect.WeaponBurn;
import com.fish_dan_.data_energistics.common.entrypoint.provider.PatternProviderRuntimeBindings;
import com.fish_dan_.data_energistics.common.multiblock.json.loading.JsonMultiBlockReloadListener;
import com.fish_dan_.data_energistics.common.orbital.OrbitalServerEvents;
import com.fish_dan_.data_energistics.common.orbital.control.OrbitalControlActionDispatcher;
import com.fish_dan_.data_energistics.common.orbital.control.OrbitalOwnershipActionDispatcher;
import com.fish_dan_.data_energistics.common.recipe.charger.DataIntegratedChargerPatternModeResolver;
import com.fish_dan_.data_energistics.common.recipe.containmentsphere.RadixContainmentSphereRightClickRecipeLogic;
import com.fish_dan_.data_energistics.common.recipe.timeshift.TimeShiftTransformLogic;
import com.fish_dan_.data_energistics.common.research.lifecycle.ResearchEvents;
import com.fish_dan_.data_energistics.common.weapon.platform.combat.GunImpactEffects;
import com.fish_dan_.data_energistics.common.weapon.platform.domain.DomainEvents;
import com.fish_dan_.data_energistics.common.weapon.platform.retention.PlatformInventoryRetention;
import com.fish_dan_.data_energistics.config.DataEnergisticsConfiguration;
import com.fish_dan_.data_energistics.utils.input.SyncedKeyMapping;

import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import org.jspecify.annotations.Nullable;

@EventBusSubscriber(modid = Data_Energistics.MOD_ID)
public final class CommonEventListener {

    private static volatile @Nullable MinecraftServer stoppingServer;

    private CommonEventListener() {}

    @SubscribeEvent
    public static void registerReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new JsonMultiBlockReloadListener(DEVerticalMultiBlocks.JSON_MULTI_BLOCKS));
        ResearchEvents.addReloadListener(event);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        SyncedKeyMapping.onPlayerLogout(event);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onPlayerClone(PlayerEvent.Clone event) {
        SyncedKeyMapping.onPlayerClone(event);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onEntityJoined(EntityJoinLevelEvent event) {
        DataMimeticFieldBlockEntity.captureSimulatedSpawnedDrops(event);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDrops(LivingDropsEvent event) {
        PlatformInventoryRetention.drops(event);
        if (!event.isCanceled()) {
            DataMimeticFieldBlockEntity.captureSimulatedDeathDrops(event);
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        DomainEvents.rightBlock(event);
        if (!event.isCanceled()) {
            RadixContainmentSphereRightClickRecipeLogic.onRightClickBlock(event);
        }
    }

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        GunImpactEffects.tick(event);
        TimeShiftTransformLogic.onEntityTick(event);
        RadixStasisAiLogic.onEntityTick(event);
        WeaponBurn.tick(event);
    }

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        GunImpactEffects.suppressNativeStellarDamage(event);
        if (!event.isCanceled()) {
            WeaponBurn.vanillaBurn(event);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onServerStarting(ServerStartingEvent event) {
        stoppingServer = null;
        TrinityPlanningGatewayLifecycle.start(DataEnergisticsConfiguration.INSTANCE.server.trinity.crafting);
        try {
            TrinityDispatchProposalLifecycle.start();
        } catch (RuntimeException exception) {
            TrinityPlanningGatewayLifecycle.stop();
            throw exception;
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        stoppingServer = event.getServer();
        ResearchEvents.stopping(event);
        OrbitalServerEvents.onServerStopping(event);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void resetKeysOnServerStopped(ServerStoppedEvent event) {
        SyncedKeyMapping.onServerStopped(event);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        try {
            try {
                OrbitalControlActionDispatcher.clearPreviews(event.getServer());
            } finally {
                OrbitalOwnershipActionDispatcher.clear(event.getServer());
            }
            PatternProviderRuntimeBindings.clearLiveBindings();
        } finally {
            try {
                DataIntegratedChargerPatternModeResolver.clearCache();
            } finally {
                try {
                    DataTeleportAnchorBlockEntity.clearRuntimeAnchorCache();
                } finally {
                    try {
                        TrinityDispatchProposalLifecycle.stop();
                    } finally {
                        try {
                            TrinityPlanningGatewayLifecycle.stop();
                        } finally {
                            if (stoppingServer == event.getServer()) {
                                stoppingServer = null;
                            }
                        }
                    }
                }
            }
        }
    }

    public static boolean isStopping(MinecraftServer server) {
        return stoppingServer == server;
    }
}
