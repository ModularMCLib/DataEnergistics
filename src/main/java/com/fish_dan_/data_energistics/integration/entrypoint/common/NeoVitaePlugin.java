package com.fish_dan_.data_energistics.integration.entrypoint.common;

import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsEntrypoint;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsPlugin;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsRegistry;
import com.fish_dan_.data_energistics.api.registry.worldenergy.DigitalSupplyInterfaceRegistration;
import com.fish_dan_.data_energistics.integration.magic.neovitae.digitalsupply.NeoVitaeDigitalSupplyAdapter;
import com.fish_dan_.data_energistics.integration.magic.neovitae.packaged.AraVitaeAdapter;
import com.fish_dan_.data_energistics.integration.magic.neovitae.packaged.HellfireForgeAdapter;
import com.fish_dan_.data_energistics.integration.magic.neovitae.packaged.TabulaVitaeAdapter;

@DataEnergisticsEntrypoint(requiredMods = "neovitae")
public final class NeoVitaePlugin implements DataEnergisticsPlugin {

    @Override
    public void register(DataEnergisticsRegistry registry) {
        registry.packagedCrafting().register(new AraVitaeAdapter());
        registry.packagedCrafting().register(new HellfireForgeAdapter());
        registry.packagedCrafting().register(new TabulaVitaeAdapter());
        NeoVitaeDigitalSupplyAdapter adapter = new NeoVitaeDigitalSupplyAdapter();
        registry.digitalSupplyInterfaces().register(new DigitalSupplyInterfaceRegistration(
                adapter.id(), adapter.resources(), adapter));
    }
}
