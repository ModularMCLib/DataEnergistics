package com.fish_dan_.data_energistics.integration.entrypoint;

import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsEntrypoint;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsPlugin;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsRegistry;
import com.fish_dan_.data_energistics.integration.magic.occultism.packaged.OccultismRitualAdapter;
import com.fish_dan_.data_energistics.integration.magic.occultism.packaged.SpiritFireAdapter;

@DataEnergisticsEntrypoint(requiredMods = "occultism")
public final class OccultismPackagedCrafting implements DataEnergisticsPlugin {

    @Override
    public void register(DataEnergisticsRegistry registry) {
        registry.packagedCrafting().register(new SpiritFireAdapter());
        registry.packagedCrafting().register(new OccultismRitualAdapter());
    }
}
