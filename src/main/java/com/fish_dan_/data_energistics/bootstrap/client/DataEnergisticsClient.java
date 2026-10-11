package com.fish_dan_.data_energistics.bootstrap.client;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.client.gui.DataEnergisticsTextureEditorResources;
import com.fish_dan_.data_energistics.client.hud.orbital.OrbitalControlHudLayer;
import com.fish_dan_.data_energistics.client.key.ClientAeKeyRenderers;
import com.fish_dan_.data_energistics.client.particle.RadixLossParticle;
import com.fish_dan_.data_energistics.client.particle.RailSteamParticle;
import com.fish_dan_.data_energistics.client.registry.DEItemColors;
import com.fish_dan_.data_energistics.client.registry.DEKeyMappings;
import com.fish_dan_.data_energistics.client.registry.adaptive.AdaptivePatternProviderToolbarFactories;
import com.fish_dan_.data_energistics.client.render.item.crossbow.CrossbowModelLoader;
import com.fish_dan_.data_energistics.client.render.item.crossbow.plasma.PlasmaTextureColors;
import com.fish_dan_.data_energistics.client.render.tooltip.DigitalStorageDepotClientTooltipComponent;
import com.fish_dan_.data_energistics.configuration.client.ConfigurationClientRegistrar;
import com.fish_dan_.data_energistics.integration.MOD;
import com.fish_dan_.data_energistics.integration.library.curios.equipment.client.CuriosDollRendererRegistry;
import com.fish_dan_.data_energistics.integration.map.ftbchunks.client.FtbChunksOrbitalAdapter;
import com.fish_dan_.data_energistics.integration.map.xaero.client.XaeroWorldMapOrbitalAdapter;
import com.fish_dan_.data_energistics.item.depot.DigitalStorageDepotTooltipComponent;
import com.fish_dan_.data_energistics.registry.DEFluids;
import com.fish_dan_.data_energistics.registry.DEParticles;
import com.fish_dan_.data_energistics.registry.DEStorageCells;

import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
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
        event.enqueueWork(ClientAeKeyRenderers::register);
    }

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(DEKeyMappings.OPEN_PATTERN_PROVIDER);
        event.register(DEKeyMappings.RENAME_PATTERN_PROVIDER);
        event.register(DEKeyMappings.TOGGLE_DIGITAL_STORAGE_DEPOT_BUCKET_MODE);
        event.register(DEKeyMappings.OPEN_ORBITAL_CONTROL);
        event.register(DEKeyMappings.TOGGLE_ORBITAL_CONTROL_HUD);
        event.register(DEKeyMappings.PREVIOUS_TRINITY_CYCLE);
        event.register(DEKeyMappings.NEXT_TRINITY_CYCLE);
        event.register(DEKeyMappings.TOGGLE_CROSSBOW_RAIL);
        event.register(DEKeyMappings.TOGGLE_CROSSBOW_ARMS);
    }

    @SubscribeEvent
    public static void onRegisterScreens(RegisterMenuScreensEvent event) {
        ClientScreenRegistrar.register(event);
    }

    @SubscribeEvent
    public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        OrbitalControlHudLayer.register(event);
    }

    @SubscribeEvent
    public static void onRegisterClientTooltipComponents(RegisterClientTooltipComponentFactoriesEvent event) {
        event.register(DigitalStorageDepotTooltipComponent.class, DigitalStorageDepotClientTooltipComponent::new);
    }

    @SubscribeEvent
    public static void onRegisterParticleProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(DEParticles.RADIX_LOSS.get(), RadixLossParticle.Provider::new);
        event.registerSpriteSet(DEParticles.RAIL_STEAM.get(), RailSteamParticle.Provider::new);
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        ClientRendererRegistrar.register(event);
    }

    @SubscribeEvent
    public static void onRegisterGeometryLoaders(ModelEvent.RegisterGeometryLoaders event) {
        event.register(Data_Energistics.id("star_shard"), new CrossbowModelLoader());
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
        if (MOD.isCuriosLoaded()) {
            CuriosDollRendererRegistry.register();
        }
        if (MOD.isXaeroWorldMapLoaded()) {
            XaeroWorldMapOrbitalAdapter.register();
        }
        if (MOD.isFtbChunksLoaded()) {
            FtbChunksOrbitalAdapter.register();
        }
        ClientGameEventRegistrar.register();
    }
}
