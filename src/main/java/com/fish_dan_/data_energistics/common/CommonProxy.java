package com.fish_dan_.data_energistics.common;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.blockentity.tower.network.domain.TowerGridServices;
import com.fish_dan_.data_energistics.common.capability.registration.CommonCapabilityRegistrar;
import com.fish_dan_.data_energistics.common.crafting.dynamic.DynamicCraftingOutputAdapters;
import com.fish_dan_.data_energistics.common.crafting.virtual.VirtualCraftingOutputAdapters;
import com.fish_dan_.data_energistics.common.data.DEAE2Keys;
import com.fish_dan_.data_energistics.common.data.DEAttachments;
import com.fish_dan_.data_energistics.common.data.DEBlockEntities;
import com.fish_dan_.data_energistics.common.data.DEBlocks;
import com.fish_dan_.data_energistics.common.data.DEChunkGenerators;
import com.fish_dan_.data_energistics.common.data.DECreativeTabs;
import com.fish_dan_.data_energistics.common.data.DEDataComponents;
import com.fish_dan_.data_energistics.common.data.DEEntities;
import com.fish_dan_.data_energistics.common.data.DEFluids;
import com.fish_dan_.data_energistics.common.data.DEGameEvents;
import com.fish_dan_.data_energistics.common.data.DEItems;
import com.fish_dan_.data_energistics.common.data.DEMenus;
import com.fish_dan_.data_energistics.common.data.DEMobEffects;
import com.fish_dan_.data_energistics.common.data.DEParticles;
import com.fish_dan_.data_energistics.common.data.DERecipes;
import com.fish_dan_.data_energistics.common.data.DESounds;
import com.fish_dan_.data_energistics.common.data.DEStructures;
import com.fish_dan_.data_energistics.common.data.DEUpgrades;
import com.fish_dan_.data_energistics.common.data.DEVerticalMultiBlocks;
import com.fish_dan_.data_energistics.common.entrypoint.DataEnergisticsEntrypointLoader;
import com.fish_dan_.data_energistics.common.entrypoint.DataEnergisticsRegistrySnapshot;
import com.fish_dan_.data_energistics.common.entrypoint.machine.CraftingMachineCapacityAdapters;
import com.fish_dan_.data_energistics.common.entrypoint.machine.PatternUploadWorkstationAdapters;
import com.fish_dan_.data_energistics.common.entrypoint.provider.PatternProviderRuntimeBindings;
import com.fish_dan_.data_energistics.common.entrypoint.terminal.UniversalTerminalAdapters;
import com.fish_dan_.data_energistics.common.event.CommonEventRegistrar;
import com.fish_dan_.data_energistics.common.input.KeyActions;
import com.fish_dan_.data_energistics.common.item.depot.DigitalStorageDepotKeyContainerItemStrategy;
import com.fish_dan_.data_energistics.common.network.DEPayloads;
import com.fish_dan_.data_energistics.common.orbital.control.ui.OrbitalControlPlayerMenu;
import com.fish_dan_.data_energistics.common.orbital.endpoint.OrbitalEndpointChunkTickets;
import com.fish_dan_.data_energistics.common.orbital.map.OrbitalClaimHints;
import com.fish_dan_.data_energistics.common.research.lifecycle.ResearchRegistrationEvents;
import com.fish_dan_.data_energistics.common.terminal.player.session.TerminalSessions;
import com.fish_dan_.data_energistics.common.trinity.host.TrinityDataCoreSyncAccessors;
import com.fish_dan_.data_energistics.common.weapon.platform.ui.WeaponPlatformMenu;
import com.fish_dan_.data_energistics.configuration.DataEnergisticsConfiguration;
import com.fish_dan_.data_energistics.configuration.DataExtractorRulesConfiguration;
import com.fish_dan_.data_energistics.integration.MOD;
import com.fish_dan_.data_energistics.integration.ae.advancedae.upgrades.AdvancedAeUpgradeRegistration;
import com.fish_dan_.data_energistics.integration.ae.ae2.dataflow.GenericKeyItemExportStrategy;
import com.fish_dan_.data_energistics.integration.ae.ae2.patternprovider.adaptive.AdaptivePatternProviderResolver;
import com.fish_dan_.data_energistics.integration.ae.ae2cs.upgrades.Ae2CrystalScienceUpgradeRegistration;
import com.fish_dan_.data_energistics.integration.ae.appflux.upgrades.AppliedFluxUpgradeRegistration;
import com.fish_dan_.data_energistics.integration.ae.appliedcreate.upgrades.AppliedCreateUpgradeRegistration;
import com.fish_dan_.data_energistics.integration.ae.extendedae.upgrades.ExtendedAeUpgradeRegistration;
import com.fish_dan_.data_energistics.integration.ae.megacells.upgrades.MegaCellsUpgradeRegistration;
import com.fish_dan_.data_energistics.integration.library.curios.equipment.CuriosDataDistributionConnectorAccess;
import com.fish_dan_.data_energistics.integration.library.curios.equipment.CuriosOrbitalControlTerminalAccess;
import com.fish_dan_.data_energistics.integration.library.ftbultimine.mining.DataCrystalPickaxeFtbUltimineCompat;
import com.fish_dan_.data_energistics.integration.map.ftbchunks.FtbChunksOrbitalClaimHints;
import com.fish_dan_.data_energistics.util.input.SyncedKeyMappings;

import appeng.api.features.HotkeyAction;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.DispenserBlock;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

public class CommonProxy {

    public static void init(IEventBus modEventBus) {
        CommonProxy instance = new CommonProxy();

        TrinityDataCoreSyncAccessors.init();
        Data_Energistics.LOGGER.debug(
                "Registered Configuration schemas {} and {}",
                DataEnergisticsConfiguration.HOLDER.getConfigId(),
                DataExtractorRulesConfiguration.HOLDER.getConfigId());

        // Plugin discovery must complete before AE2 receives its AEKeyType RegisterEvent. The resulting snapshot is
        // shared by the key-type event, common setup installation and every runtime consumer.
        DataEnergisticsEntrypointLoader.initialize();

        TowerGridServices.init();

        DEFluids.register(modEventBus);
        DEGameEvents.register(modEventBus);
        DEDataComponents.register(modEventBus);
        DEAttachments.register(modEventBus);
        DEBlocks.register(modEventBus);
        DEItems.register(modEventBus);
        DEMobEffects.register(modEventBus);
        DEParticles.register(modEventBus);
        DEEntities.register(modEventBus);
        DEBlockEntities.register(modEventBus);
        DECreativeTabs.register(modEventBus);
        DEMenus.register(modEventBus);
        DERecipes.register(modEventBus);
        DESounds.register(modEventBus);
        DEStructures.register(modEventBus);
        DEChunkGenerators.register(modEventBus);
        DEVerticalMultiBlocks.init();
        DEUpgrades.registerPartModels();
        modEventBus.addListener(instance::onRegister);
        modEventBus.addListener(instance::commonSetup);
        modEventBus.addListener(EventPriority.LOWEST, instance::registerDepotContainerItemStrategies);
        modEventBus.addListener(EventPriority.LOWEST, instance::registerGenericKeyWorldExportStrategies);
        modEventBus.addListener(DEAE2Keys::register);
        modEventBus.addListener(CommonCapabilityRegistrar::register);
        modEventBus.addListener(CommonCapabilityRegistrar::registerPartCapabilities);
        modEventBus.addListener(DEPayloads::register);
        modEventBus.addListener(KeyActions::register);
        modEventBus.addListener(OrbitalEndpointChunkTickets::registerController);

        CommonEventRegistrar.register();
        modEventBus.register(new ResearchRegistrationEvents());
    }

    private void onRegister(RegisterEvent event) {
        // Match GTM's registry phase; the item registry event gives this a single initialization point.
        if (event.getRegistryKey().equals(Registries.ITEM)) SyncedKeyMappings.init();
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            DataEnergisticsRegistrySnapshot snapshot = DataEnergisticsEntrypointLoader.snapshot();
            UniversalTerminalAdapters.install(snapshot.universalTerminalRegistrations());
            VirtualCraftingOutputAdapters.install(snapshot.virtualCraftingOutputAdapters());
            DynamicCraftingOutputAdapters.install(snapshot.dynamicCraftingOutputAdapters());
            CraftingMachineCapacityAdapters.install(snapshot.craftingMachineCapacityRegistrations());
            PatternUploadWorkstationAdapters.install(snapshot.patternUploadWorkstationRegistrations());
            PatternProviderRuntimeBindings.install(
                    snapshot.patternProviderRegistrations(),
                    snapshot.patternProviderWorkstationSourceRegistrations());
            AdaptivePatternProviderResolver.install(snapshot.adaptivePatternProviderRegistrations());
            DEUpgrades.init();
            DispenserBlock.registerProjectileBehavior(DEItems.DATA_TRIDENT.get());
            if (MOD.ADVANCED_AE.isLoaded()) {
                AdvancedAeUpgradeRegistration.register();
            }
            if (MOD.AE2CS.isLoaded()) {
                Ae2CrystalScienceUpgradeRegistration.register();
            }
            if (MOD.APPFLUX.isLoaded()) {
                AppliedFluxUpgradeRegistration.register();
            }
            if (MOD.APPLIED_CREATE.isLoaded()) {
                AppliedCreateUpgradeRegistration.register();
            }
            if (MOD.EXTENDED_AE.isLoaded()) {
                ExtendedAeUpgradeRegistration.register();
            }
            if (MOD.MEGA_CELLS.isLoaded()) {
                MegaCellsUpgradeRegistration.register();
            }
            OrbitalControlPlayerMenu.register();
            WeaponPlatformMenu.register();
            HotkeyAction.register(TerminalSessions::stowMainHand, "ae2wtlib_stow");
            if (MOD.CURIOS.isLoaded()) {
                CuriosDataDistributionConnectorAccess.register();
                CuriosOrbitalControlTerminalAccess.register();
            }
            if (MOD.FTB_CHUNKS.isLoaded()) {
                OrbitalClaimHints.install(FtbChunksOrbitalClaimHints::isClaimed);
                Data_Energistics.LOGGER.info("Registered FTB Chunks orbital tactical-map claim hints");
            }
            if (Data_Energistics.isModLoaded("ftbultimine")) {
                Data_Energistics.LOGGER.info("Registering Data Crystal Pickaxe FTB Ultimine duplicate ore integration");
                DataCrystalPickaxeFtbUltimineCompat.init();
            }
        });
    }

    private void registerDepotContainerItemStrategies(final FMLCommonSetupEvent event) {
        event.enqueueWork(DigitalStorageDepotKeyContainerItemStrategy::registerMissingStrategies);
    }

    private void registerGenericKeyWorldExportStrategies(final FMLCommonSetupEvent event) {
        event.enqueueWork(GenericKeyItemExportStrategy::registerMissingStrategies);
    }
}
