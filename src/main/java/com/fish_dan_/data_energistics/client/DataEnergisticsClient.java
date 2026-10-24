package com.fish_dan_.data_energistics.client;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.client.event.ClientGameEventRegistrar;
import com.fish_dan_.data_energistics.client.gui.DataEnergisticsTextureEditorResources;
import com.fish_dan_.data_energistics.client.hud.orbital.OrbitalControlHudLayer;
import com.fish_dan_.data_energistics.client.hud.weapon_platform.TacticalHudLayer;
import com.fish_dan_.data_energistics.client.key.AeKeyRenderers;
import com.fish_dan_.data_energistics.client.model.ClientItemModelPropertyRegistrar;
import com.fish_dan_.data_energistics.client.model.ClientModelRegistrar;
import com.fish_dan_.data_energistics.client.particle.RadixLossParticle;
import com.fish_dan_.data_energistics.client.particle.RailSteamParticle;
import com.fish_dan_.data_energistics.client.registry.ClientExtensionRegistrar;
import com.fish_dan_.data_energistics.client.registry.DEItemColors;
import com.fish_dan_.data_energistics.client.registry.DEKeyMappings;
import com.fish_dan_.data_energistics.client.registry.adaptive.AdaptivePatternProviderToolbarFactories;
import com.fish_dan_.data_energistics.client.render.ClientRendererRegistrar;
import com.fish_dan_.data_energistics.client.render.item.crossbow.CrossbowModelLoader;
import com.fish_dan_.data_energistics.client.render.item.crossbow.plasma.PlasmaTextureColors;
import com.fish_dan_.data_energistics.client.render.item.sanctifier.model.SanctifierModelLoader;
import com.fish_dan_.data_energistics.client.render.tooltip.DigitalStorageDepotClientTooltipComponent;
import com.fish_dan_.data_energistics.client.screen.ClientScreenRegistrar;
import com.fish_dan_.data_energistics.common.data.DEFluids;
import com.fish_dan_.data_energistics.common.data.DEParticles;
import com.fish_dan_.data_energistics.common.data.DEStorageCells;
import com.fish_dan_.data_energistics.common.item.depot.DigitalStorageDepotTooltipComponent;
import com.fish_dan_.data_energistics.configuration.client.ConfigurationClientRegistrar;
import com.fish_dan_.data_energistics.integration.MOD;
import com.fish_dan_.data_energistics.integration.library.curios.equipment.client.CuriosDollRendererRegistry;
import com.fish_dan_.data_energistics.integration.library.curios.equipment.client.CuriosWeaponPlatformRenderer;
import com.fish_dan_.data_energistics.integration.map.ftbchunks.client.FtbChunksOrbitalAdapter;
import com.fish_dan_.data_energistics.integration.map.xaero.client.XaeroWorldMapOrbitalAdapter;

import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

@Mod(value = Data_Energistics.MODID, dist = Dist.CLIENT)
public final class DataEnergisticsClient {

    public DataEnergisticsClient(IEventBus modEventBus) {
        DataEnergisticsTextureEditorResources.register(modEventBus);
        modEventBus.register(DataEnergisticsClient.class);
    }

    @SubscribeEvent
    public static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(new PlasmaTextureColors());
        event.registerReloadListener((ResourceManagerReloadListener) manager -> AeKeyRenderers.clearColors());
    }

    @SubscribeEvent
    public static void onRegisterItemColors(RegisterColorHandlersEvent.Item event) {
        DEItemColors.register(event);
    }

    @SubscribeEvent
    public static void onRegisterClientExtensions(RegisterClientExtensionsEvent event) {
        ClientExtensionRegistrar.register(event);
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(DataEnergisticsClient::registerClientSetup);
    }

    @SubscribeEvent
    public static void onLoadComplete(FMLLoadCompleteEvent event) {
        event.enqueueWork(AeKeyRenderers::register);
    }

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(DEKeyMappings.INTERACT_TACTICAL_HUD);
        event.register(DEKeyMappings.TOGGLE_PLATFORM_PROSPECTING);
        event.register(DEKeyMappings.OPEN_WEAPON_PLATFORM);
        event.register(DEKeyMappings.LOCK_WEAPON_PLATFORM_TARGET);
        event.register(DEKeyMappings.OPEN_PATTERN_PROVIDER);
        event.register(DEKeyMappings.RENAME_PATTERN_PROVIDER);
        event.register(DEKeyMappings.TOGGLE_DIGITAL_STORAGE_DEPOT_BUCKET_MODE);
        event.register(DEKeyMappings.OPEN_ORBITAL_CONTROL);
        event.register(DEKeyMappings.TOGGLE_ORBITAL_CONTROL_HUD);
        event.register(DEKeyMappings.MOVE_PLAYER_TERMINAL_TO_MOUSE);
        event.register(DEKeyMappings.PREVIOUS_PAGE);
        event.register(DEKeyMappings.NEXT_PAGE);
        event.register(DEKeyMappings.TOGGLE_CROSSBOW_RAIL);
        event.register(DEKeyMappings.TOGGLE_CROSSBOW_ARMS);
    }

    @SubscribeEvent
    public static void onRegisterScreens(RegisterMenuScreensEvent event) {
        ClientScreenRegistrar.register(event);
    }

    @SubscribeEvent
    public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        TacticalHudLayer.register(event);
        OrbitalControlHudLayer.register(event);
    }

    @SubscribeEvent
    public static void onRegisterClientTooltipComponents(RegisterClientTooltipComponentFactoriesEvent event) {
        event.register(DigitalStorageDepotTooltipComponent.class, DigitalStorageDepotClientTooltipComponent::new);
    }

    @SubscribeEvent
    public static void onRegisterParticleProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(DEParticles.RADIX_REDUNDANCY.get(), RadixLossParticle.Provider::new);
        event.registerSpriteSet(DEParticles.RAIL_STEAM.get(), RailSteamParticle.Provider::new);
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        ClientRendererRegistrar.register(event);
    }

    @SubscribeEvent
    public static void onAddPlayerLayers(EntityRenderersEvent.AddLayers event) {
        ClientRendererRegistrar.addLayers(event);
    }

    @SubscribeEvent
    public static void onRegisterGeometryLoaders(ModelEvent.RegisterGeometryLoaders event) {
        event.register(Data_Energistics.id("star_shard"), new CrossbowModelLoader());
        event.register(Data_Energistics.id("sanctifier"), new SanctifierModelLoader());
    }

    @SubscribeEvent
    public static void onRegisterAdditionalModels(ModelEvent.RegisterAdditional event) {
        ClientModelRegistrar.registerAdditionalModels(event);
    }

    @SubscribeEvent
    public static void onModifyBakingResult(ModelEvent.ModifyBakingResult event) {
        ClientModelRegistrar.modifyBakingResult(event);
    }

    private static void registerClientSetup() {
        AdaptivePatternProviderToolbarFactories.initialize();
        ConfigurationClientRegistrar.register();
        DEStorageCells.registerClientModels();
        RenderType translucent = RenderType.translucent();
        ItemBlockRenderTypes.setRenderLayer(DEFluids.ENDER.get(), translucent);
        ItemBlockRenderTypes.setRenderLayer(DEFluids.FLOWING_ENDER.get(), translucent);
        ItemBlockRenderTypes.setRenderLayer(DEFluids.DATA_CORROSION_LIQUID.get(), translucent);
        ItemBlockRenderTypes.setRenderLayer(DEFluids.FLOWING_DATA_CORROSION_LIQUID.get(), translucent);
        ClientItemModelPropertyRegistrar.register();
        if (MOD.CURIOS.isLoaded()) {
            CuriosDollRendererRegistry.register();
            CuriosWeaponPlatformRenderer.register();
        }
        if (MOD.XAERO_WORLD_MAP.isLoaded()) {
            XaeroWorldMapOrbitalAdapter.register();
        }
        if (MOD.FTB_CHUNKS.isLoaded()) {
            FtbChunksOrbitalAdapter.register();
        }
        ClientGameEventRegistrar.register();
    }
}
