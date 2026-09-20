package com.fish_dan_.data_energistics.bootstrap.client;

import com.fish_dan_.data_energistics.client.registry.adaptive.AdaptivePatternProviderToolbarFactories;
import com.fish_dan_.data_energistics.configuration.client.ConfigurationClientRegistrar;
import com.fish_dan_.data_energistics.integration.MOD;
import com.fish_dan_.data_energistics.integration.library.curios.equipment.client.CuriosDollRendererRegistry;
import com.fish_dan_.data_energistics.integration.map.ftbchunks.client.FtbChunksOrbitalAdapter;
import com.fish_dan_.data_energistics.integration.map.xaero.client.XaeroWorldMapOrbitalAdapter;
import com.fish_dan_.data_energistics.registry.DEStorageCells;

final class ClientSetupRegistrar {

    private ClientSetupRegistrar() {}

    static void register() {
        AdaptivePatternProviderToolbarFactories.initialize();
        ClientAeKeyRendererRegistrar.register();
        ConfigurationClientRegistrar.register();
        DEStorageCells.registerClientModels();
        ClientRenderLayerRegistrar.register();
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
